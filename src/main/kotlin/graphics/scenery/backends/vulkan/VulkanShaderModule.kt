package graphics.scenery.backends.vulkan

import graphics.scenery.spirvcrossj.*
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import graphics.scenery.BufferUtils
import java.nio.ByteBuffer
import java.util.*
import graphics.scenery.spirvcrossj.EShLanguage
import graphics.scenery.spirvcrossj.EShMessages
import graphics.scenery.utils.LazyLogger
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
 * @param[clazz] Base path for the shader module, to determine where to load the file from
 * @param[shaderCodePath] Path of the shader text file or SPIR-V binary file, relative to clazz.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

open class VulkanShaderModule(device: VkDevice, entryPoint: String, clazz: Class<*>, shaderCodePath: String) {
    protected val logger by LazyLogger()
    var shader: VkPipelineShaderStageCreateInfo
    var shaderModule: Long
    var device: VkDevice
    var uboSpecs = LinkedHashMap<String, UBOSpec>()

    data class UBOMemberSpec(val name: String, val index: Long, val offset: Long, val range: Long)
    data class UBOSpec(val name: String, val set: Long, val binding: Long, val members: LinkedHashMap<String, UBOMemberSpec>)

    init {

        logger.debug("Creating VulkanShaderModule $entryPoint, $shaderCodePath")

        this.device = device

        // check if we have a compiled version, and it's newer than the source version
        val sourceClass: Class<*>
        var codeResource = if(clazz.javaClass.getResource(shaderCodePath) != null) {
            sourceClass = clazz
            clazz.javaClass.getResource(shaderCodePath)
        } else {
            sourceClass = VulkanRenderer::class.java
            VulkanRenderer::class.java.getResource(shaderCodePath)
        }

        val actualCodePath: String

        val sourceNewer = if(shaderCodePath.toLowerCase().endsWith("spv")) {
            val sourceCodeResource = sourceClass.getResource(shaderCodePath.substringBeforeLast(".spv"))

            if(sourceCodeResource != null) {
                // a slight bias is needed here, as if both files are compiled into the
                // classes/ or jar at the same time, they'll carry the same timestamp.
                val spirvModificationDate = if(codeResource != null) {
                    Date(codeResource.openConnection().lastModified + 500)
                } else {
                    Date(0)
                }

                val sourceModificationDate = Date(sourceCodeResource.openConnection().lastModified)

                if(sourceModificationDate.after(spirvModificationDate)) {
                    logger.info("Recompiling $shaderCodePath, as source file is newer than SPV file.")
                    actualCodePath = shaderCodePath.substringBeforeLast(".spv")
                    codeResource = sourceCodeResource
                    true
                } else {
                    actualCodePath = shaderCodePath
                    false
                }
            } else {
                actualCodePath = shaderCodePath
                false
            }
        } else {
            actualCodePath = shaderCodePath
            false
        }

        var code = ByteBuffer.allocate(0)

        val spirv = if(shaderCodePath.toLowerCase().endsWith("spv") && !sourceNewer) {
            code = BufferUtils.allocateByteAndPut(codeResource.readBytes())
            code.toSPIRVBytecode()
        } else {
            logger.info("Compiling $actualCodePath to SPIR-V...")
            // code needs to be compiled first
            val program = TProgram()
            val defaultResources = libspirvcrossj.getDefaultTBuiltInResource()
            val shaderType = when (actualCodePath.substringAfterLast(".")) {
                "vert" -> EShLanguage.EShLangVertex
                "frag" -> EShLanguage.EShLangFragment
                "geom" -> EShLanguage.EShLangGeometry
                "tesc" -> EShLanguage.EShLangTessControl
                "tese" -> EShLanguage.EShLangTessEvaluation
                "comp" -> EShLanguage.EShLangCompute
                else -> { logger.warn("Unknown shader extension ." + actualCodePath.substringAfterLast(".")); 0 }
            }


            val shader = TShader(shaderType)

            var messages = EShMessages.EShMsgDefault
            messages = messages or EShMessages.EShMsgVulkanRules
            messages = messages or EShMessages.EShMsgSpvRules

            val shaderCode = arrayOf(codeResource.readText())
            shader.setStrings(shaderCode, shaderCode.size)
            shader.setAutoMapBindings(true)

            val compileFail = !shader.parse(defaultResources, 450, false, messages)
            if(compileFail) {
                logger.error("Error in shader compilation of $actualCodePath for ${clazz.simpleName}: ${shader.infoLog}")
            }

            program.addShader(shader)

            val linkFail = !program.link(EShMessages.EShMsgDefault) || !program.mapIO()

            if(!linkFail && !compileFail) {
                val tmp = IntVec()
                libspirvcrossj.glslangToSpv(program.getIntermediate(shaderType), tmp)

                code = tmp.toByteBuffer()

                tmp
            } else {
                logger.error("Error in shader linking of $actualCodePath for ${clazz.simpleName}: ${program.infoLog}")
                IntVec()
            }
        }

        val compiler = CompilerGLSL(spirv)

        val uniformBuffers = compiler.shaderResources.uniformBuffers

        for(i in 0..uniformBuffers.size()-1) {
            val res = uniformBuffers.get(i.toInt())
            logger.debug("${res.name}, set=${compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)}, binding=${compiler.getDecoration(res.id, Decoration.DecorationBinding)}")

            val members = LinkedHashMap<String, UBOMemberSpec>()
            val activeRanges = compiler.getActiveBufferRanges(res.id)

            // record all members of the UBO struct, order by index, and store them to UBOSpec.members
            // for further use
            members.putAll((0..activeRanges.size()-1).map {
                val range = activeRanges.get(it.toInt())
                val name = compiler.getMemberName(res.baseTypeId, range.index)

                name.to(UBOMemberSpec(
                    compiler.getMemberName(res.baseTypeId, range.index),
                    range.index,
                    range.offset,
                    range.range))
            }.sortedBy { it.second.index })

            val ubo = UBOSpec(res.name,
                set = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet),
                binding = compiler.getDecoration(res.id, Decoration.DecorationBinding),
                members = members)

            // only add the UBO spec if it doesn't already exist, and has more than 0 members
            // SPIRV UBOs may have 0 members, if they are not used in the actual shader code
            if(!uboSpecs.contains(res.name) && ubo.members.size > 0) {
                uboSpecs.put(res.name, ubo)
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
        if(compiler.shaderResources.sampledImages.size() > 0) {
            val res = compiler.shaderResources.sampledImages.get(0)
            if (res.name != "ObjectTextures") {
                uboSpecs.put(res.name, UBOSpec("inputs",
                    set = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet),
                    binding = 0,
                    members = LinkedHashMap<String, UBOMemberSpec>()))
            }
        }

        val inputs = compiler.shaderResources.stageInputs
        if(inputs.size() > 0) {
            for (i in 0..inputs.size()-1) {
                logger.debug("$shaderCodePath: ${inputs.get(i.toInt()).name}")
            }
        }

        val moduleCreateInfo = VkShaderModuleCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            .pNext(NULL)
            .pCode(code)
            .flags(0)

        val shaderModule = memAllocLong(1)
        vkCreateShaderModule(device, moduleCreateInfo, null, shaderModule)
        this.shaderModule = shaderModule.get(0)

        moduleCreateInfo.free()
        memFree(shaderModule)

        this.shader = VkPipelineShaderStageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(getStageFromFilename(shaderCodePath))
            .module(this.shaderModule)
            .pName(memUTF8(entryPoint))
            .pNext(NULL)
    }

    private fun IntVec.toByteBuffer(): ByteBuffer {
        val buf = BufferUtils.allocateByte(this.size().toInt()*4)
        val ib = buf.asIntBuffer()

        for (i in 0..this.size()-1) {
            ib.put(this[i.toInt()].toInt())
        }

        return buf
    }

    private fun ByteBuffer.toSPIRVBytecode(): IntVec {
        val bytecode = IntVec()
        val ib = this.asIntBuffer()

        while(ib.hasRemaining()) {
            bytecode.pushBack(1L*ib.get())
        }

        return bytecode
    }

    protected fun getStageFromFilename(shaderCodePath: String): Int {
        val ending = if(shaderCodePath.substringAfterLast(".").startsWith("spv")) {
            shaderCodePath.substringBeforeLast(".").substringAfterLast(".")
        } else {
            shaderCodePath.substringAfterLast(".")
        }

        val type = when(ending) {
           "vert" -> VK_SHADER_STAGE_VERTEX_BIT
           "geom" -> VK_SHADER_STAGE_GEOMETRY_BIT
           "tesc" -> VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT
           "tese" -> VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT
           "frag" -> VK_SHADER_STAGE_FRAGMENT_BIT
           "comp" -> VK_SHADER_STAGE_COMPUTE_BIT
            else -> {
                logger.error("Unknown shader type: $ending for $shaderCodePath")
                -1
            }
        }

        logger.trace("$shaderCodePath has type $type")

        return type
    }

    companion object {
        @Suppress("UNUSED")
        fun createFromSPIRV(device: VkDevice, name: String, clazz: Class<*>, sourceFile: String): VulkanShaderModule {
            return VulkanShaderModule(device, name, clazz, sourceFile)
        }

        @Suppress("UNUSED")
        fun createFromSource(device: VkDevice, name: String, clazz: Class<*>, sourceFile: String): VulkanShaderModule {
            return VulkanShaderModule(device, name, clazz, sourceFile)
        }
    }
}
