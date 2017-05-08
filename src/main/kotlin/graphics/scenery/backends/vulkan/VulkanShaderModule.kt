package graphics.scenery.backends.vulkan

import graphics.scenery.spirvcrossj.*
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import graphics.scenery.BufferUtils
import java.nio.ByteBuffer
import java.util.*
import graphics.scenery.spirvcrossj.EShLanguage
import graphics.scenery.spirvcrossj.EShMessages
import java.nio.Buffer


/**
 * Vulkan Object State class. Saves texture, UBO, pipeline and vertex buffer state.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanShaderModule(device: VkDevice, entryPoint: String, clazz: Class<*>, shaderCodePath: String) {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanShaderModule")
    var shader: VkPipelineShaderStageCreateInfo
    var shaderModule: Long
    var device: VkDevice
    var uboSpecs = LinkedHashMap<String, UBOSpec>()

    data class UBOSpec(val name: String, val set: Long, val binding: Long)

    init {

        logger.debug("Creating VulkanShaderModule $entryPoint, $shaderCodePath")

        this.device = device
        val codeResource = if(clazz.javaClass.getResource(shaderCodePath) != null) {
            clazz.javaClass.getResource(shaderCodePath)
        } else {
            VulkanRenderer::class.java.getResource(shaderCodePath)
        }

        var code = ByteBuffer.allocate(0)

        val spirv = if(shaderCodePath.toLowerCase().endsWith("spv")) {
            code = BufferUtils.allocateByteAndPut(codeResource.readBytes())
            code.toSPIRVBytecode()
        } else {
            logger.info("Compiling $shaderCodePath to SPIR-V...")
            // code needs to be compiled first
            val program = TProgram()
            val defaultResources = libspirvcrossj.getDefaultTBuiltInResource()
            val shaderType = when (shaderCodePath.substringAfterLast(".")) {
                "vert" -> EShLanguage.EShLangVertex
                "frag" -> EShLanguage.EShLangFragment
                "geom" -> EShLanguage.EShLangGeometry
                "tesc" -> EShLanguage.EShLangTessControl
                "tese" -> EShLanguage.EShLangTessEvaluation
                "comp" -> EShLanguage.EShLangCompute
                else -> { logger.warn("Unknown shader extension ." + shaderCodePath.substringAfterLast(".")); 0 }
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
                logger.error("Error in shader compilation of $shaderCodePath for ${clazz.simpleName}: ${shader.infoLog}")
            }

            program.addShader(shader)

            val linkFail = !program.link(EShMessages.EShMsgDefault) || !program.mapIO()

            if(!linkFail && !compileFail) {
                val tmp = IntVec()
                libspirvcrossj.glslangToSpv(program.getIntermediate(shaderType), tmp)

                code = tmp.toByteBuffer()

                tmp
            } else {
                logger.error("Error in shader linking of $shaderCodePath for ${clazz.simpleName}: ${program.infoLog}")
                IntVec()
            }
        }

        val compiler = CompilerGLSL(spirv)

        val uniformBuffers = compiler.shaderResources.uniformBuffers

        for(i in 0..uniformBuffers.size()-1) {
            val res = uniformBuffers.get(i.toInt())
            logger.debug("${res.name}, set=${compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet)}, binding=${compiler.getDecoration(res.id, Decoration.DecorationBinding)}")

            val ubo = UBOSpec(res.name,
                set = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet),
                binding = compiler.getDecoration(res.id, Decoration.DecorationBinding))

            if(!uboSpecs.contains(res.name)) {
                uboSpecs.put(res.name, ubo)
            }
        }

        // inputs are summarized into one descriptor set
        if(compiler.shaderResources.sampledImages.size() > 0) {
            val res = compiler.shaderResources.sampledImages.get(0)
            if(res.name != "ObjectTextures") {
                uboSpecs.put(res.name, UBOSpec("inputs",
                    set = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet),
                    binding = 0))
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
