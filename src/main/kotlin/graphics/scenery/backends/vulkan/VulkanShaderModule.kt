package graphics.scenery.backends.vulkan

import graphics.scenery.backends.ShaderConsistencyException
import graphics.scenery.backends.ShaderPackage
import graphics.scenery.backends.ShaderType
import graphics.scenery.backends.Shaders
import graphics.scenery.spirvcrossj.CompilerGLSL
import graphics.scenery.spirvcrossj.Decoration
import graphics.scenery.spirvcrossj.ExecutionMode
import graphics.scenery.utils.LazyLogger
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
    protected val logger by LazyLogger()
    var shader: VkPipelineShaderStageCreateInfo
    var shaderModule: Long
    var uboSpecs = LinkedHashMap<String, UBOSpec>()
    var pushConstantSpecs = LinkedHashMap<String, PushConstantSpec>()
    val type: ShaderType = sp.type
    val localSize: Triple<Int, Int, Int>
    private var deallocated: Boolean = false
    private var signature: ShaderSignature

    /**
     * Specification of UBO members, storing [name], [index] in the buffer, [offset] from the beginning,
     * and size of the member as [range].
     */
    data class UBOMemberSpec(val name: String, val index: Long, val offset: Long, val range: Long)

    /** Types an UBO can have */
    enum class UBOSpecType {
        UniformBuffer,
        SampledImage1D, SampledImage2D, SampledImage3D,
        Image1D, Image2D, Image3D,
        StorageBuffer, StorageBufferDynamic
    }

    /**
     * Specification of an UBO, storing [name], descriptor [set], [binding], [type], and a set of [members].
     * Can be an array, in that case, [size] > 1.
     */
    data class UBOSpec(val name: String, var set: Long, var binding: Long, val type: UBOSpecType, val members: LinkedHashMap<String, UBOMemberSpec>, val size: Int = 1)

    /**
     * Specification for push constants, containing [name] and [members].
     */

    data class PushConstantSpec(val name: String, val members: LinkedHashMap<String, UBOMemberSpec>)

    private data class ShaderSignature(val device: VulkanDevice, val p: ShaderPackage)

    init {
        logger.debug("Processing shader package with code=${sp.codePath}, spirv=${sp.spirvPath} and main entry point $entryPoint")
        signature = ShaderSignature(device, sp)

        if(sp.spirv == null) {
            throw IllegalStateException("Shader Package is expected to have SPIRV bytecode at this point")
        }

        val spirv = sp.getSPIRVBytecode()

        val compiler = CompilerGLSL(spirv)

        val uniformBuffers = compiler.shaderResources.uniformBuffers
        val pushConstants = compiler.shaderResources.pushConstantBuffers

        val x = compiler.getExecutionModeArgument(ExecutionMode.ExecutionModeLocalSize, 0).toInt()
        val y = compiler.getExecutionModeArgument(ExecutionMode.ExecutionModeLocalSize, 1).toInt()
        val z = compiler.getExecutionModeArgument(ExecutionMode.ExecutionModeLocalSize, 2).toInt()

        logger.debug("Local size: $x $y $z")

        if((x == 0 || y == 0 || y == 0) && type == ShaderType.ComputeShader) {
            logger.error("Compute local sizes $x, $y, $z must not be zero, setting to 1.")
        }

        localSize = Triple(maxOf(x, 1), maxOf(y, 1), maxOf(z, 1))

        for(i in 0 until uniformBuffers.capacity()) {
            val res = uniformBuffers.get(i)
            logger.debug("${res.name}, set=${compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)}, binding=${compiler.getDecoration(res.id, Decoration.DecorationBinding)}")

            val members = LinkedHashMap<String, UBOMemberSpec>()
            val activeRanges = compiler.getActiveBufferRanges(res.id)

            // record all members of the UBO struct, order by index, and store them to UBOSpec.members
            // for further use
            members.putAll((0 until activeRanges.capacity()).map {
                val range = activeRanges.get(it)
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
                type = UBOSpecType.UniformBuffer,
                members = members)

            // only add the UBO spec if it doesn't already exist, and has more than 0 members
            // SPIRV UBOs may have 0 members, if they are not used in the actual shader code
            if(!uboSpecs.contains(res.name) && ubo.members.size > 0) {
                uboSpecs[res.name] = ubo
            }
        }

        for(i in 0 until pushConstants.capacity()) {
            val res = pushConstants.get(i)
            val activeRanges = compiler.getActiveBufferRanges(res.id)
            val members = LinkedHashMap<String, UBOMemberSpec>()

            logger.debug("Push constant: ${res.name}, id=${compiler.getDecoration(res.id, Decoration.DecorationConstant)}")

            members.putAll((0 until activeRanges.capacity()).map {
                val range = activeRanges.get(it)
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
        (0 until compiler.shaderResources.sampledImages.capacity()).forEach { samplerId ->
            val res = compiler.shaderResources.sampledImages.get(samplerId)
            val setId = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)
            val type = compiler.getType(res.typeId)

            val arraySize = if(type.array.capacity() > 0) {
                type.array.get(0).toInt()
            } else {
                1
            }

            val samplerType = type.image.type
            val samplerDim = type.image.dim

            val name = if(res.name.startsWith("Input")) {
                if(!inputSets.contains(setId)) {
                    inputSets.add(setId)
                }

                "Inputs-$setId"
            } else {
                res.name
            }

            if(uboSpecs.containsKey(name)) {
                logger.debug("Adding inputs member ${res.name}/$name type=${type.basetype}, a=$arraySize, type=$samplerType, dim=$samplerDim")
                uboSpecs[name]?.let { spec ->
                    spec.members[res.name] = UBOMemberSpec(res.name, spec.members.size.toLong(), 0L, 0L)
                    spec.binding = minOf(spec.binding, compiler.getDecoration(res.id, Decoration.DecorationBinding))
                }
            } else {
                val bindingId = compiler.getDecoration(res.id, Decoration.DecorationBinding)
                logger.debug("Adding inputs UBO, ${res.name}/$name, set=$setId, binding=$bindingId, type=${type.basetype}, a=$arraySize, type=$samplerType, dim=$samplerDim")
                uboSpecs[name] = UBOSpec(name,
                    set = setId,
                    binding = bindingId,
                    type = when(samplerDim) {
                        0 -> UBOSpecType.SampledImage1D
                        1 -> UBOSpecType.SampledImage2D
                        2 -> UBOSpecType.SampledImage3D
                        else -> throw IllegalArgumentException("samplerDim cannot be $samplerDim.")
                    },
                    members = LinkedHashMap(),
                    size = arraySize)

                if(name.startsWith("Inputs")) {
                    uboSpecs[name]?.members?.put(res.name, UBOMemberSpec(res.name, 0L, 0L, 0L))
                }
            }
        }

        (0 until compiler.shaderResources.storageImages.capacity()).forEach { imageId ->
            val res = compiler.shaderResources.storageImages.get(imageId)
            val setId = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)
            val type = compiler.getType(res.typeId)

            val arraySize = if(type.array.capacity() > 0) {
                type.array.get(0).toInt()
            } else {
                1
            }

            val imageType = type.image.type
            val imageDim = type.image.dim

//            val name = if(res.name.startsWith("Input") || res.name.startsWith("Output")) {
//                if(!inputSets.contains(setId)) {
//                    inputSets.add(setId)
//                }
//
//                "Inputs-$setId"
//            } else {
//                res.name
//            }
            val name = res.name

            if(uboSpecs.containsKey(name)) {
                logger.debug("Adding image load/store member ${res.name}/$name type=${type.basetype}, a=$arraySize, type=$imageType, dim=$imageDim")
                uboSpecs[name]?.let { spec ->
                    spec.members[res.name] = UBOMemberSpec(res.name, spec.members.size.toLong(), 0L, 0L)
                    spec.binding = minOf(spec.binding, compiler.getDecoration(res.id, Decoration.DecorationBinding))
                }
            } else {
                val bindingId = compiler.getDecoration(res.id, Decoration.DecorationBinding)
                logger.debug("Adding image load/store UBO, ${res.name}/$name, set=$setId, binding=$bindingId, type=${type.basetype}, a=$arraySize, type=$imageType, dim=$imageDim")
                uboSpecs[name] = UBOSpec(name,
                    set = setId,
                    binding = bindingId,
                    type = when(imageDim) {
                        0 -> UBOSpecType.Image1D
                        1 -> UBOSpecType.Image2D
                        2 -> UBOSpecType.Image3D
                        else -> throw IllegalArgumentException("samplerDim cannot be $imageDim.")
                    },
                    members = LinkedHashMap(),
                    size = arraySize)

                if(name.startsWith("Inputs")) {
                    uboSpecs[name]?.members?.put(res.name, UBOMemberSpec(res.name, 0L, 0L, 0L))
                }
            }
        }

        val inputs = compiler.shaderResources.stageInputs
        if(inputs.capacity() > 0) {
            for (i in 0 until inputs.capacity()) {
                logger.debug("${sp.toShortString()}: ${inputs.get(i).name}")
            }
        }

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
