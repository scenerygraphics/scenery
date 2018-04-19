package vkn

import glfw_.appBuffer.ptr
import glfw_.advance
import org.lwjgl.vulkan.*


//fun VmDescriptorBufferInfo(): VkDescriptorBufferInfo = VkDescriptorBufferInfo.malloc()
//fun VmDescriptorBufferInfo(capacity: Int): VkDescriptorBufferInfo.Buffer = VkDescriptorBufferInfo.malloc(capacity)
fun cVkDescriptorBufferInfo(): VkDescriptorBufferInfo = VkDescriptorBufferInfo.calloc()
fun cVkDescriptorBufferInfo(capacity: Int): VkDescriptorBufferInfo.Buffer = VkDescriptorBufferInfo.calloc(capacity)


fun VbApplicationInfo(block: VkApplicationInfo.() -> Unit): VkApplicationInfo = VkApplicationInfo.create(ptr.advance(VkApplicationInfo.SIZEOF)).also(block)


inline fun cVkPipelineVertexInputStateCreateInfo(block: VkPipelineVertexInputStateCreateInfo.() -> Unit): VkPipelineVertexInputStateCreateInfo {
    val res = VkPipelineVertexInputStateCreateInfo.calloc()
    res.type = VkStructureType.PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO
    res.block()
    return res
}

inline fun cVkSubmitInfo(block: VkSubmitInfo.() -> Unit): VkSubmitInfo {
    val res = VkSubmitInfo.calloc()
    res.type = VkStructureType.SUBMIT_INFO
    res.block()
    return res
}

fun cVkVertexInputBindingDescription(capacity: Int, block:VkVertexInputBindingDescription.() -> Unit): VkVertexInputBindingDescription.Buffer {
    val res = VkVertexInputBindingDescription.calloc(capacity)
    res[0].block()
    return res
}
