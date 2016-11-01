package scenery.backends.vulkan

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT
import org.lwjgl.vulkan.KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR
import org.lwjgl.vulkan.KHRSurface.VK_ERROR_NATIVE_WINDOW_IN_USE_KHR
import org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR
import org.lwjgl.vulkan.VK10.*
import org.slf4j.LoggerFactory
import java.nio.LongBuffer

fun VkCommandBuffer.endCommandBuffer() {
    if(vkEndCommandBuffer(this) != VK_SUCCESS) {
        throw AssertionError("Failed to end command buffer $this")
    }
}

fun VkCommandBuffer.endCommandBuffer(device: VkDevice, commandPool: Long, queue: VkQueue?, flush: Boolean = true, dealloc: Boolean = false) {
    if (this.address() === MemoryUtil.NULL) {
        return
    }

    if(vkEndCommandBuffer(this) != VK_SUCCESS) {
        throw AssertionError("Failed to end command buffer $this")
    }

    if(flush && queue != null) {
        scenery.backends.vulkan.VU.run(MemoryUtil.memAllocPointer(1).put(this).flip(), "endCommandBuffer") {
            val submitInfo = VkSubmitInfo.calloc(1)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(this)

            vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE)
            vkQueueWaitIdle(queue)
        }
    }

    if(dealloc) {
        vkFreeCommandBuffers(device, commandPool, this)
    }
}

fun VkPhysicalDevice.getMemoryType(typeBits: Int, memoryFlags: Int): Pair<Boolean, Int> {
    var found = false
    var bits = typeBits
    val properties = VkPhysicalDeviceMemoryProperties.calloc()
    vkGetPhysicalDeviceMemoryProperties(this, properties)

    for (i in 0..properties.memoryTypeCount() - 1) {
        if (bits and 1 == 1) {
            if ((properties.memoryTypes(i).propertyFlags() and memoryFlags) == memoryFlags) {
                found = true
                return found.to(i)
            }
        }

        bits = bits shr 1
    }

    System.err.println("Memory type $memoryFlags not found for device")
    return false.to(0)
}

class VU {

    companion object VU {

        inline fun <T: LongBuffer> run(receiver: T, name: String, function: T.() -> Int): Long {
            var result = function.invoke(receiver)

            if(result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed.")
            }

            val ret = receiver.get(0)
            MemoryUtil.memFree(receiver)

            return ret
        }

        inline fun <T: LongBuffer> run(receiver: T, name: String, function: T.() -> Int, cleanup: T.() -> Any, free: Boolean = true): Long {
            var result = function.invoke(receiver)

            if(result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed.")
                cleanup.invoke(receiver)
            }

            val ret = receiver.get(0)
            if(free) {
                MemoryUtil.memFree(receiver)
            }

            cleanup.invoke(receiver)

            return ret
        }

        inline fun <T: PointerBuffer> run(receiver: T, name: String, function: T.() -> Int): Long {
            var result = function.invoke(receiver)

            if(result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed.")
            }

            val ret = receiver.get(0)
            MemoryUtil.memFree(receiver)

            return ret
        }

        inline fun <T: PointerBuffer> run(receiver: T, name: String, function: T.() -> Int, cleanup: T.() -> Any, free: Boolean = true): Long {
            var result = function.invoke(receiver)

            if(result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed.")
                cleanup.invoke(receiver)
            }

            val ret = receiver.get(0)
            if(free) {
                MemoryUtil.memFree(receiver)
            }

            cleanup.invoke(receiver)

            return ret
        }

        fun deviceTypeToString(deviceType: Int): String {
            return when(deviceType) {
                0 -> "other"
                1 -> "Integrated GPU"
                2 -> "Discrete GPU"
                3 -> "Virtual GPU"
                4 -> "CPU"
                else -> "Unknown device type"
            }
        }

        fun vendorToString(vendor: Int): String =
            when(vendor) {
                0x1002 -> "AMD"
                0x10DE -> "Nvidia"
                0x8086 -> "Intel"
                else -> "(Unknown vendor)"
            }

        fun decodeDriverVersion(version: Int) =
            Triple(
                version and 0xFFC00000.toInt() shr 22,
                version and 0x003FF000 shr 12,
                version and 0x00000FFF
            )

        fun driverVersionToString(version: Int) =
            decodeDriverVersion(version).toList().joinToString(".")

        /**
         * Translates a Vulkan `VkResult` value to a String describing the result.

         * @param result
         * *            the `VkResult` value
         * *
         * *
         * @return the result description
         */
        fun translate(result: Int): String {
            when (result) {
            // Success codes
                VK_SUCCESS -> return "Command successfully completed."
                VK_NOT_READY -> return "A fence or query has not yet completed."
                VK_TIMEOUT -> return "A wait operation has not completed in the specified time."
                VK_EVENT_SET -> return "An event is signaled."
                VK_EVENT_RESET -> return "An event is unsignaled."
                VK_INCOMPLETE -> return "A return array was too small for the result."
                VK_SUBOPTIMAL_KHR -> return "A swapchain no longer matches the surface properties exactly, but can still be used to present to the surface successfully."

            // Error codes
                VK_ERROR_OUT_OF_HOST_MEMORY -> return "A host memory allocation has failed."
                VK_ERROR_OUT_OF_DEVICE_MEMORY -> return "A device memory allocation has failed."
                VK_ERROR_INITIALIZATION_FAILED -> return "Initialization of an object could not be completed for implementation-specific reasons."
                VK_ERROR_DEVICE_LOST -> return "The logical or physical device has been lost."
                VK_ERROR_MEMORY_MAP_FAILED -> return "Mapping of a memory object has failed."
                VK_ERROR_LAYER_NOT_PRESENT -> return "A requested layer is not present or could not be loaded."
                VK_ERROR_EXTENSION_NOT_PRESENT -> return "A requested extension is not supported."
                VK_ERROR_FEATURE_NOT_PRESENT -> return "A requested feature is not supported."
                VK_ERROR_INCOMPATIBLE_DRIVER -> return "The requested version of Vulkan is not supported by the driver or is otherwise incompatible for implementation-specific reasons."
                VK_ERROR_TOO_MANY_OBJECTS -> return "Too many objects of the type have already been created."
                VK_ERROR_FORMAT_NOT_SUPPORTED -> return "A requested format is not supported on this device."
                VK_ERROR_SURFACE_LOST_KHR -> return "A surface is no longer available."
                VK_ERROR_NATIVE_WINDOW_IN_USE_KHR -> return "The requested window is already connected to a VkSurfaceKHR, or to some other non-Vulkan API."
                VK_ERROR_OUT_OF_DATE_KHR -> return """A surface has changed in such a way that it is no longer compatible with the swapchain, and further presentation requests using the
                +"swapchain will fail. Applications must query the new surface properties and recreate their swapchain if they wish to continue presenting to the surface"""
                    VK_ERROR_INCOMPATIBLE_DISPLAY_KHR -> return "The display used by a swapchain does not use the same presentable image layout, or is incompatible in a way that prevents sharing an" + " image."
                VK_ERROR_VALIDATION_FAILED_EXT -> return "A validation layer found an error."
                else -> return String.format("%s [%d]", "Unknown", Integer.valueOf(result))
            }
        }

        fun setImageLayout(commandBuffer: VkCommandBuffer, image: Long, aspectMask: Int, oldImageLayout: Int, newImageLayout: Int, range: VkImageSubresourceRange) {
            val imageMemoryBarrier = VkImageMemoryBarrier.calloc(1)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .pNext(NULL)
                .oldLayout(oldImageLayout)
                .newLayout(newImageLayout)
                .image(image)
                .subresourceRange(range)
                .srcAccessMask(when(oldImageLayout) {
                    VK_IMAGE_LAYOUT_PREINITIALIZED -> VK_ACCESS_HOST_WRITE_BIT
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                    VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> VK_ACCESS_TRANSFER_READ_BIT
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK_ACCESS_TRANSFER_WRITE_BIT
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> VK_ACCESS_SHADER_READ_BIT

                    VK_IMAGE_LAYOUT_UNDEFINED -> 0
                    else -> 0
                })

            when(newImageLayout) {
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> imageMemoryBarrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> {
                    imageMemoryBarrier.srcAccessMask(imageMemoryBarrier.srcAccessMask() or VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                }
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> {
                    imageMemoryBarrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    imageMemoryBarrier.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                }
                VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> {
                    imageMemoryBarrier.dstAccessMask(imageMemoryBarrier.dstAccessMask() or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                }
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> {
                    if(imageMemoryBarrier.srcAccessMask() == 0) {
                        imageMemoryBarrier.dstAccessMask(VK_ACCESS_HOST_WRITE_BIT or VK_ACCESS_TRANSFER_WRITE_BIT)
                    }

                    imageMemoryBarrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                }
            }

            val srcStageFlags = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
            val dstStageFlags = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT

            vkCmdPipelineBarrier(commandBuffer,
                srcStageFlags,
                dstStageFlags,
                0,
                null,
                null,
                imageMemoryBarrier
            )
        }

        fun setImageLayout(commandBuffer: VkCommandBuffer, image: Long, aspectMask: Int, oldImageLayout: Int, newImageLayout: Int) {
            val range = VkImageSubresourceRange.calloc()
                .aspectMask(aspectMask)
                .baseMipLevel(0)
                .levelCount(1)
                .layerCount(1)

            setImageLayout(commandBuffer, image, aspectMask, oldImageLayout, newImageLayout, range)
        }

        fun createDeviceQueue(device: VkDevice, queueFamilyIndex: Int): VkQueue {
            val pQueue = MemoryUtil.memAllocPointer(1)
            vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue)
            val queue = pQueue.get(0)
            MemoryUtil.memFree(pQueue)
            return VkQueue(queue, device)
        }

        fun newCommandBuffer(device: VkDevice, commandPool: Long, level: Int = VK_COMMAND_BUFFER_LEVEL_PRIMARY): VkCommandBuffer {
            val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(level)
                .commandBufferCount(1)

            val commandBuffer = VU.run(MemoryUtil.memAllocPointer(1),"Creating command buffer",
                { vkAllocateCommandBuffers(device, cmdBufAllocateInfo, this) },
                { cmdBufAllocateInfo.free() })

            return VkCommandBuffer(commandBuffer, device)
        }

        fun newCommandBuffer(device: VkDevice, commandPool: Long, level: Int = VK_COMMAND_BUFFER_LEVEL_PRIMARY, autostart: Boolean = false): VkCommandBuffer {
            val cmdBuf = newCommandBuffer(device, commandPool, level)

            if(autostart) {
                val cmdBufInfo = VkCommandBufferBeginInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT)
                    .pNext(MemoryUtil.NULL)

                vkBeginCommandBuffer(cmdBuf, cmdBufInfo)
            }

            return cmdBuf
        }


    }


}
