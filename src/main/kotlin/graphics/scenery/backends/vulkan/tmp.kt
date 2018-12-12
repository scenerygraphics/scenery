package graphics.scenery.backends.vulkan

import kool.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Platform
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.nvkGetPhysicalDeviceSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.KHRWin32Surface.VK_KHR_WIN32_SURFACE_EXTENSION_NAME
import org.lwjgl.vulkan.KHRXlibSurface.VK_KHR_XLIB_SURFACE_EXTENSION_NAME
import org.lwjgl.vulkan.MVKMacosSurface.VK_MVK_MACOS_SURFACE_EXTENSION_NAME
import vkk.*
import vkk.entities.VkDynamicStateBuffer
import vkk.entities.VkImageView
import vkk.entities.VkImageViewBuffer
import vkk.entities.VkSurface


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

fun vk.DescriptorImageInfo() = VkDescriptorImageInfo.calloc()
fun MemoryStack.VkSurfaceCapabilitiesKHR() = VkSurfaceCapabilitiesKHR.callocStack(this)

fun MemoryStack.VkSwapchainCreateInfoKHR() = VkSwapchainCreateInfoKHR.callocStack(this).apply { type = VkStructureType.SWAPCHAIN_CREATE_INFO_KHR }
fun MemoryStack.VkSwapchainCreateInfoKHR(block: VkSwapchainCreateInfoKHR.() -> Unit) = VkSwapchainCreateInfoKHR().also(block)
fun MemoryStack.VkImageViewCreateInfo() = VkImageViewCreateInfo.callocStack(this).apply { type = VkStructureType.IMAGE_VIEW_CREATE_INFO }
fun MemoryStack.VkImageViewCreateInfo(block: VkImageViewCreateInfo.() -> Unit) = VkImageViewCreateInfo().also(block)

fun VkPhysicalDevice.getSurfaceCapabilitiesKHR(surface: VkSurface, surfaceCapabilities: VkSurfaceCapabilitiesKHR) {
    nvkGetPhysicalDeviceSurfaceCapabilitiesKHR(this, surface.L, surfaceCapabilities.adr)
}

fun VkImageViewCreateInfo.components(rgba: VkComponentSwizzle) {
    components.apply {
        r = rgba
        g = rgba
        b = rgba
        a = rgba
    }
}

fun VkPresentInfoKHR() = VkPresentInfoKHR.calloc().apply { type = VkStructureType.PRESENT_INFO_KHR }

fun VkWriteDescriptorSet() = VkWriteDescriptorSet.calloc().apply { type = VkStructureType.WRITE_DESCRIPTOR_SET }
fun VkWriteDescriptorSet(size: Int) = VkWriteDescriptorSet.calloc(size).apply {
    for (i in this)
        i.type = VkStructureType.WRITE_DESCRIPTOR_SET
}

val Platform.surfaceExtension: String?
    get() = when (this) {
        Platform.WINDOWS -> VK_KHR_WIN32_SURFACE_EXTENSION_NAME
        Platform.LINUX -> VK_KHR_XLIB_SURFACE_EXTENSION_NAME
        Platform.MACOSX -> VK_MVK_MACOS_SURFACE_EXTENSION_NAME
        else -> null
    }
