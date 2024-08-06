package graphics.scenery.backends.vulkan

import graphics.scenery.backends.*
import graphics.scenery.utils.lazyLogger
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import java.util.concurrent.ConcurrentHashMap


/**
 * Vulkan Shader Module
 *
 * Class facilitating the use of shaders in Vulkan. Supports loading SPIR-V binaries and compiling GLSL
 * shader text files to SPIR-V binaries, with introspection.
 *
 * When loading a SPIR-V binary, VulkanShaderModule will check if a newer GLSL text file with the same name
 * exists and load that.
 *
 * @param[device] The Vulkan device to use (VkDevice)
 * @param[entryPoint] Customizable main entry point for the shader, usually "main"
 * @param[sp] A [ShaderPackage] originating from the [Shaders] class.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

open class VulkanShaderModule(val device: VulkanDevice, entryPoint: String, val sp: ShaderPackage) {
    protected val logger by lazyLogger()
    var shader: VkPipelineShaderStageCreateInfo
    var shaderModule: Long
    var uboSpecs = LinkedHashMap<String, ShaderIntrospection.UBOSpec>()
    var pushConstantSpecs = LinkedHashMap<String, ShaderIntrospection.PushConstantSpec>()
    val type: ShaderType = sp.type
    val localSize: Triple<Int, Int, Int>
    private var deallocated: Boolean = false
    private var signature: ShaderSignature


    private data class ShaderSignature(val device: VulkanDevice, val p: ShaderPackage)

    init {
        logger.debug("Processing shader package with code=${sp.codePath}, spirv=${sp.spirvPath} and main entry point $entryPoint")
        signature = ShaderSignature(device, sp)

        if(sp.spirv == null) {
            throw IllegalStateException("Shader Package is expected to have SPIRV bytecode at this point")
        }

        val spirv = sp.getSPIRVOpcodes()!!

        val intro = ShaderIntrospection(spirv)

        val localSizes = intro.localSizes()

        logger.debug("Local size: $localSizes")

        if((localSizes.x == 0 || localSizes.y == 0 || localSizes.z == 0) && type == ShaderType.ComputeShader) {
            logger.error("Compute local sizes $localSizes must not be zero, setting to 1.")
        }

        localSize = Triple(maxOf(localSizes.x, 1), maxOf(localSizes.y, 1), maxOf(localSizes.z, 1))

        (intro.uniformBuffers()+intro.storageBuffers()).forEach { ubo ->
            // only add the UBO spec if it doesn't already exist, and has more than 0 members
            // SPIRV UBOs may have 0 members, if they are not used in the actual shader code
            if(!uboSpecs.contains(ubo.name) && ubo.members.size > 0) {
                uboSpecs[ubo.name] = ubo
            }
        }

        intro.pushContants().forEach { pcs ->
            if(!pushConstantSpecs.contains(pcs.name) && pcs.members.size > 0) {
                pushConstantSpecs[pcs.name] = pcs
            }
        }

        // inputs are summarized into one descriptor set
        val inputSets = mutableSetOf<Long>()

        intro.sampledImages().forEach { sampledImage ->
            val name = if (sampledImage.name.startsWith("Input")) {
                if (!inputSets.contains(sampledImage.set)) {
                    inputSets.add(sampledImage.set)
                }

                "Inputs-${sampledImage.set}"
            } else {
                sampledImage.name
            }

            if (uboSpecs.containsKey(name)) {
                logger.debug("Adding inputs member ${sampledImage.name}/$name type=${sampledImage.type}, arraySize=${sampledImage.size}.")
                uboSpecs[name]?.let { spec ->
                    spec.members[sampledImage.name] =
                        ShaderIntrospection.UBOMemberSpec(sampledImage.name, spec.members.size.toLong(), 0L, 0L)
                    spec.binding = minOf(spec.binding, sampledImage.binding)
                }
            } else {
                logger.debug("Adding inputs UBO, ${sampledImage.name}, set=${sampledImage.set}, binding=${sampledImage.binding}, type=${sampledImage.type}, a=${sampledImage.size}")
                uboSpecs[name] = sampledImage

                if(name.startsWith("Inputs")) {
                    uboSpecs[name]?.members?.put(sampledImage.name,
                        ShaderIntrospection.UBOMemberSpec(sampledImage.name, 0L, 0L, 0L)
                    )
                }
            }
        }

        intro.storageImages().forEach { storageImage ->
            val name = storageImage.name

            if (uboSpecs.containsKey(storageImage.name)) {
                logger.debug("Adding inputs member ${storageImage.name}/$name type=${storageImage.type}, arraySize=${storageImage.size}.")
                uboSpecs[name]?.let { spec ->
                    spec.members[storageImage.name] =
                        ShaderIntrospection.UBOMemberSpec(storageImage.name, spec.members.size.toLong(), 0L, 0L)
                    spec.binding = minOf(spec.binding, storageImage.binding)
                }
            } else {
                logger.debug("Adding inputs UBO, ${storageImage.name}, set=${storageImage.set}, binding=${storageImage.binding}, type=${storageImage.type}, a=${storageImage.size}")

                uboSpecs[storageImage.name] = storageImage
                if(name.startsWith("Inputs")) {
                    uboSpecs[name]?.members?.put(storageImage.name,
                        ShaderIntrospection.UBOMemberSpec(storageImage.name, 0L, 0L, 0L)
                    )
                }
            }
        }

//        val inputs = compiler.shaderResources.stageInputs
//        if(inputs.capacity() > 0) {
//            for (i in 0 until inputs.capacity()) {
//                logger.debug("${sp.toShortString()}: ${inputs.get(i).name}")
//            }
//        }

        // consistency check to not have the same set used multiple twice
        uboSpecs.entries
            .groupBy { it.value.set }
            .forEach { (set, specs) ->
                if(specs.groupBy { it.value.binding }.any { it.value.size > 1 }) {
                    throw ShaderConsistencyException("Shader package defines descriptor set $set multiple times (${specs.size} times, for UBOs ${specs.joinToString { it.key }}). This is not allowed. ")
                }
            }

        val code = memAlloc(sp.spirv.size)
        code.put(sp.spirv)
        code.flip()

        val moduleCreateInfo = VkShaderModuleCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            .pNext(NULL)
            .pCode(code)
            .flags(0)

        this.shaderModule = VU.getLong("Creating shader module",
            { vkCreateShaderModule(device.vulkanDevice, moduleCreateInfo, null, this) },
            { moduleCreateInfo.free(); })

        this.shader = VkPipelineShaderStageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(sp.type.toVulkanShaderStage())
            .module(this.shaderModule)
            .pName(memUTF8(entryPoint))
            .pNext(NULL)

        // re-sort UBO specs according to set and binding
        val sortedSpecs = uboSpecs.entries
            .sortedBy { it.value.set }
            .map { it.key to it.value }
        uboSpecs.clear()
        sortedSpecs.forEach { uboSpecs[it.first] = it.second }

        val tag = if(sp.codePath == null && sp.spirvPath == null) {
            "(procedurally generated shader)"
        } else {
            "${sp.codePath}/${sp.spirvPath}"
        }

        device.tag(this.shaderModule, VulkanDevice.VulkanObjectType.ShaderModule, "Shader Module for $tag")

        logger.debug("Created Vulkan Shader Module ${this.shaderModule.toHexString()}")
    }

    protected fun ShaderType.toVulkanShaderStage() = when(this) {
        ShaderType.VertexShader -> VK_SHADER_STAGE_VERTEX_BIT
        ShaderType.GeometryShader -> VK_SHADER_STAGE_GEOMETRY_BIT
        ShaderType.TessellationControlShader -> VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT
        ShaderType.TessellationEvaluationShader -> VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT
        ShaderType.FragmentShader -> VK_SHADER_STAGE_FRAGMENT_BIT
        ShaderType.ComputeShader -> VK_SHADER_STAGE_COMPUTE_BIT
    }

    /**
     * Closes this Vulkan shader module, deallocating all of it's resources.
     * If the module has already been closed, no deallocation takes place.
     */
    fun close() {
        if(!deallocated) {
            vkDestroyShaderModule(device.vulkanDevice, shader.module(), null)
            shaderModuleCache.remove(signature.hashCode())

            memFree(shader.pName())
            shader.free()

            deallocated = true
        }
    }

    /**
     * Factory and cache methods for [VulkanShaderModule].
     */
    companion object {
        private val shaderModuleCache = ConcurrentHashMap<Int, VulkanShaderModule>()

        /**
         * Creates a new [VulkanShaderModule] or returns it from the cache.
         * Must be given a [ShaderPackage] [sp], a [VulkanDevice] [device], and the name
         * for the main [entryPoint].
         */
        fun getFromCacheOrCreate(device: VulkanDevice, entryPoint: String, sp: ShaderPackage): VulkanShaderModule {
            val signature = ShaderSignature(device, sp).hashCode()

            return if(sp.disableCaching) {
                VulkanShaderModule(device, entryPoint, sp)
            } else {
                shaderModuleCache.getOrPut(signature) {
                    VulkanShaderModule(device, entryPoint, sp)
                }
            }
        }

        /**
         * Clears the shader cache.
         */
        fun clearCache() {
            shaderModuleCache.forEach { it.value.close() }
            shaderModuleCache.clear()
        }
    }
}
