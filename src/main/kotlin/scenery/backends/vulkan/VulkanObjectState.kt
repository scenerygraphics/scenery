package scenery.backends.vulkan

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import scenery.NodeMetadata
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ulrik on 9/27/2016.
 */
class VulkanObjectState : NodeMetadata {
    override val consumers: MutableList<String> = ArrayList()

    var initialized = false
    var inputState = VkPipelineVertexInputStateCreateInfo.calloc()
    var bindingDescriptions = VkVertexInputBindingDescription.calloc(1)
    var attributeDescriptions = VkVertexInputAttributeDescription.calloc(3)

    var vertexBuffers = ConcurrentHashMap<String, Long>()
    var createInfo: VkPipelineVertexInputStateCreateInfo? = null

    var pipeline = VulkanRenderer.Pipeline()

    constructor() {
        consumers.add("VulkanRenderer")
    }

}
