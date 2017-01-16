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

/**
 * Created by ulrik on 9/27/2016.
 */

class VulkanShaderModule(device: VkDevice, entryPoint: String, shaderCodePath: String) {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanShaderModule")
    var shader: VkPipelineShaderStageCreateInfo
    var shaderModule: Long
    var device: VkDevice
    var uboSpecs = LinkedHashMap<String, UBOSpec>()

    data class UBOSpec(val name: String, val set: Long, val binding: Long)

    init {
        Loader.loadNatives()

        logger.debug("Creating VulkanShaderModule $entryPoint, $shaderCodePath")

        this.device = device
        val code = BufferUtils.allocateByteAndPut(this.javaClass.getResource(shaderCodePath).readBytes())
        val compiler = CompilerGLSL(code.toSPIRVBytecode())

        val uniformBuffers = compiler.getShaderResources().uniformBuffers

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
        if(compiler.getShaderResources().sampledImages.size() > 0) {
            val res = compiler.getShaderResources().sampledImages.get(0)
            if(res.name != "ObjectTextures") {
                uboSpecs.put(res.name, UBOSpec("inputs",
                    set = compiler.getDecoration(res.id, Decoration.DecorationDescriptorSet),
                    binding = 0))
            }
        }

        val inputs = compiler.getShaderResources().stageInputs
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

    fun destroy() {
        vkDestroyShaderModule(device, this.shaderModule, null)
    }

    companion object {
        fun createFromSPIRV(device: VkDevice, name: String, sourceFile: String): VulkanShaderModule {
            return VulkanShaderModule(device, name, sourceFile)
        }

        fun createFromSource(device: VkDevice, name: String, sourceFile: String): VulkanShaderModule {
            return VulkanShaderModule(device, name, sourceFile)
        }
    }
}
