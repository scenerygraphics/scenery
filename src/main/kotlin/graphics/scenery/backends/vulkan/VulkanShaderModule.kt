package graphics.scenery.backends.vulkan

import graphics.scenery.backends.ShaderPackage
import graphics.scenery.backends.ShaderType
import graphics.scenery.backends.Shaders
import graphics.scenery.spirvcrossj.*
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.LinkedHashMap


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

open class VulkanShaderModule(val device: VulkanDevice, entryPoint: String, sp: ShaderPackage) {
    protected val logger by LazyLogger()
    var shader: VkPipelineShaderStageCreateInfo
    var shaderModule: Long
    var uboSpecs = LinkedHashMap<String, UBOSpec>()
    var pushConstantSpecs = LinkedHashMap<String, PushConstantSpec>()
    private var deallocated: Boolean = false
    private var signature: ShaderSignature

    /**
     * Specification of UBO members, storing [name], [index] in the buffer, [offset] from the beginning,
     * and size of the member as [range].
     */
    data class UBOMemberSpec(val name: String, val index: Long, val offset: Long, val range: Long)

    /**
     * Specification of an UBO, storing [name], descriptor [set], [binding], and a set of [members].
     */
    data class UBOSpec(val name: String, var set: Long, var binding: Long, val members: LinkedHashMap<String, UBOMemberSpec>)

    /**
     * Specification for push constants, containing [name] and [members].
     */
    data class PushConstantSpec(val name: String, val members: LinkedHashMap<String, UBOMemberSpec>)

    private data class ShaderSignature(val device: VulkanDevice, val p: ShaderPackage)

    init {
        signature = ShaderSignature(device, sp)

        if(sp.spirv == null) {
            throw IllegalStateException("Shader Package is expected to have SPIRV bytecode at this point")
        }

        val spirv = sp.getSPIRVBytecode()

        val compiler = CompilerGLSL(spirv)

        val uniformBuffers = compiler.shaderResources.uniformBuffers
        val pushConstants = compiler.shaderResources.pushConstantBuffers

        for(i in 0 until uniformBuffers.size()) {
            val res = uniformBuffers.get(i.toInt())
            logger.debug("${res.name}, set=${compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)}, binding=${compiler.getDecoration(res.id, Decoration.DecorationBinding)}")

            val members = LinkedHashMap<String, UBOMemberSpec>()
            val activeRanges = compiler.getActiveBufferRanges(res.id)

            // record all members of the UBO struct, order by index, and store them to UBOSpec.members
            // for further use
            members.putAll((0 until activeRanges.size()).map {
                val range = activeRanges.get(it.toInt())
                val name = compiler.getMemberName(res.baseTypeId, range.index)

                name to UBOMemberSpec(
                    compiler.getMemberName(res.baseTypeId, range.index),
                    range.index,
                    range.offset,
                    range.range)
            }.sortedBy { it.second.index })

            val ubo = UBOSpec(res.name,
                set = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet),
                binding = compiler.getDecoration(res.id, Decoration.DecorationBinding),
                members = members)

            // only add the UBO spec if it doesn't already exist, and has more than 0 members
            // SPIRV UBOs may have 0 members, if they are not used in the actual shader code
            if(!uboSpecs.contains(res.name) && ubo.members.size > 0) {
                uboSpecs[res.name] = ubo
            }
        }

        for(i in 0 until pushConstants.size()) {
            val res = pushConstants.get(i.toInt())
            val activeRanges = compiler.getActiveBufferRanges(res.id)
            val members = LinkedHashMap<String, UBOMemberSpec>()

            logger.debug("Push constant: ${res.name}, id=${compiler.getDecoration(res.id, Decoration.DecorationConstant)}")

            members.putAll((0 until activeRanges.size()).map {
                val range = activeRanges.get(it.toInt())
                val name = compiler.getMemberName(res.baseTypeId, range.index)

                name to UBOMemberSpec(
                    compiler.getMemberName(res.baseTypeId, range.index),
                    range.index,
                    range.offset,
                    range.range
                )
            }.sortedBy { it.second.index })

            val pcs = PushConstantSpec(res.name,
                members = members)

            if(!pushConstantSpecs.contains(res.name) && pcs.members.size > 0) {
                pushConstantSpecs[res.name] = pcs
            }
        }

        /* Updated version:
       for(i in 0..compiler.shaderResources.sampledImages.size()-1) {
        // inputs are summarized into one descriptor set
            val res = compiler.shaderResources.sampledImages.get(i.toInt())
            logger.info("Adding textures ${res.name} with set=${compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)}")

            // FIXME: Here we assume at the moment that we either have only input textures (from framebuffers), or only object textures
            val name = if(res.name == "ObjectTextures" || res.name == "VolumeTextures") {
                "ObjectTextures"
            } else {
                "inputs"
            }

            uboSpecs.put(res.name, UBOSpec(name,
                    set = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet),
                    binding = 0,
                    members = LinkedHashMap<String, UBOMemberSpec>()))
         */
        // inputs are summarized into one descriptor set
        val inputSets = mutableSetOf<Long>()
        (0 until compiler.shaderResources.sampledImages.size()).forEach { samplerId ->
            val res = compiler.shaderResources.sampledImages.get(samplerId.toInt())
            val setId = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)

            val name = if(res.name.startsWith("Input")) {
                if(!inputSets.contains(setId)) {
                    inputSets.add(setId)
                }

                "Inputs-$setId"
            } else {
                res.name
            }

            if(uboSpecs.containsKey(name)) {
                logger.debug("Adding inputs member ${res.name}/$name")
                uboSpecs[name]?.let { spec ->
                    spec.members[res.name] = UBOMemberSpec(res.name, spec.members.size.toLong(), 0L, 0L)
                    spec.binding = minOf(spec.binding, compiler.getDecoration(res.id, Decoration.DecorationBinding))
                }
            } else {
                logger.debug("Adding inputs UBO, ${res.name}/$name, set=$setId")
                uboSpecs.put(name, UBOSpec(name,
                    set = setId,
                    binding = compiler.getDecoration(res.id, Decoration.DecorationBinding),
                    members = LinkedHashMap()))

                if(name.startsWith("Inputs")) {
                    uboSpecs[name]?.members?.put(res.name, UBOMemberSpec(res.name, 0L, 0L, 0L))
                }
            }
        }

        val inputs = compiler.shaderResources.stageInputs
        if(inputs.size() > 0) {
            for (i in 0 until inputs.size()) {
                logger.debug("${sp.toShortString()}: ${inputs.get(i.toInt()).name}")
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

            return shaderModuleCache.getOrPut(signature) {
                VulkanShaderModule(device, entryPoint, sp)
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
