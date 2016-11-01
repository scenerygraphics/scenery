package scenery.backends.vulkan

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import scenery.NodeMetadata
import java.nio.LongBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ulrik on 9/27/2016.
 */
class VulkanObjectState : NodeMetadata {
    override val consumers: MutableList<String> = ArrayList()

    var initialized = false
    var isIndexed = false
    var indexOffset = 0
    var indexCount = 0
    var vertexCount = 0
    var inputState = VkPipelineVertexInputStateCreateInfo.calloc()
    var bindingDescriptions = VkVertexInputBindingDescription.calloc(1)
    var attributeDescriptions = VkVertexInputAttributeDescription.calloc(3)

    var vertexBuffers = ConcurrentHashMap<String, VulkanBuffer>()

    var pipeline = VulkanRenderer.Pipeline()

    var UBO: VulkanRenderer.UBO? = null

    constructor() {
        consumers.add("VulkanRenderer")
    }

}
