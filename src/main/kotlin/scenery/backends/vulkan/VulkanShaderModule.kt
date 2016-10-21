package scenery.backends.vulkan

import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scenery.BufferUtils

/**
 * Created by ulrik on 9/27/2016.
 */

class VulkanShaderModule(device: VkDevice, entryPoint: String, shaderCodePath: String) {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanShaderModule")
    var shader: VkPipelineShaderStageCreateInfo
    var shaderModule: Long
    var device: VkDevice

    init {
        logger.info("Creating VulkanShaderModule $entryPoint, $shaderCodePath")

        this.device = device
        val code = BufferUtils.allocateByteAndPut(this.javaClass.getResource(shaderCodePath).readBytes())
        val moduleCreateInfo = VkShaderModuleCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            .pNext(NULL)
            .pCode(code)
            .flags(0)

        val shaderModule = memAllocLong(1)
        vkCreateShaderModule(device, moduleCreateInfo, null, shaderModule)
        this.shaderModule = shaderModule.get(0)
        memFree(shaderModule)

        this.shader = VkPipelineShaderStageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(getStageFromFilename(shaderCodePath))
            .module(this.shaderModule)
            .pName(memUTF8(entryPoint))
            .pNext(NULL)
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
