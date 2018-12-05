package graphics.scenery.backends.vulkan

import kool.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import vkk.VkDynamicState
import vkk.VkResult
import vkk.entities.VkDynamicStateBuffer
import vkk.entities.VkImageView
import vkk.entities.VkImageViewBuffer
import vkk.vk


infix fun VkPhysicalDevice.createDevice(createInfo: VkDeviceCreateInfo): Pair<VkDevice, VkResult> =
    stak {
        val adr = it.nmalloc(Pointer.POINTER_SIZE, Pointer.POINTER_SIZE)
        val res = VK10.nvkCreateDevice(this, createInfo.adr, NULL, adr)
        VkDevice(MemoryUtil.memGetAddress(adr), this, createInfo) to VkResult(res)
    }


fun vk.AttachmentDescription(size: Int, init: (Int) -> VkAttachmentDescription): VkAttachmentDescription.Buffer {
    val res = VkAttachmentDescription.callocStack(size)
    for (i in res.indices)
        res[i] = init(i)
    return res
}

fun vk.ImageViewBuffer(size: Int, init: (Int) -> VkImageView) = VkImageViewBuffer(stackGet().LongBuffer(size) { init(it).L })

fun vkDynamicStateBufferOf(dynamicState: VkDynamicState): VkDynamicStateBuffer = VkDynamicStateBuffer(intBufferOf(dynamicState.i))

fun vkDynamicStateBufferOf(dynamicState0: VkDynamicState,
                                       dynamicState1: VkDynamicState): VkDynamicStateBuffer =
    VkDynamicStateBuffer(intBufferOf(dynamicState0.i, dynamicState1.i))

fun vkDynamicStateBufferOf(dynamicState0: VkDynamicState,
                                       dynamicState1: VkDynamicState,
                                       dynamicState2: VkDynamicState): VkDynamicStateBuffer =
    VkDynamicStateBuffer(intBufferOf(dynamicState0.i, dynamicState1.i, dynamicState2.i))
