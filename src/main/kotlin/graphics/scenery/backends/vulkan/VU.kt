package graphics.scenery.backends.vulkan

import graphics.scenery.Blending
import graphics.scenery.utils.lazyLogger
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT
import org.lwjgl.vulkan.KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR
import org.lwjgl.vulkan.KHRSurface.VK_ERROR_NATIVE_WINDOW_IN_USE_KHR
import org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR
import org.lwjgl.vulkan.VK10.*
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.nio.IntBuffer
import java.nio.LongBuffer

/**
 * Returns a given Long as hex-formatted string.
 *
 * @returns The long value as hex string.
 */
fun Long.toHexString(): String {
    return String.format("0x%X", this).lowercase()
}

/**
 * Returns a given BigInteger as hex-formatted string.
 *
 * @returns The BigInteger value as hex string.
 */
fun BigInteger.toHexString(): String {
    return "0x${this.toString(16)}".lowercase()
}

/**
 * Ends the recording of a command buffer.
 */
fun VkCommandBuffer.endCommandBuffer() {
    if(vkEndCommandBuffer(this) != VK_SUCCESS) {
        throw AssertionError("Failed to end command buffer $this")
    }
}

/**
 * Ends the recording of a command buffer on the given [device] using [commandPool] on the queue [queue].
 * If [flush] is set, ending the command buffer will trigger submission. If [dealloc] is set, the command buffer
 * will be deallocated after running it.
 *
 * [submitInfoPNext], [signalSemaphores], [waitSemaphores] and [waitDstStageMask] can be used to further fine-grain
 * the submission process.
 */
fun VkCommandBuffer.endCommandBuffer(device: VulkanDevice, commandPool: Long,
                                     queue: VulkanDevice.QueueWithMutex, flush: Boolean = true,
                                     dealloc: Boolean = false,
                                     submitInfoPNext: Pointer? = null,
                                     signalSemaphores: LongBuffer? = null, waitSemaphores: LongBuffer? = null,
                                     waitDstStageMask: IntBuffer? = null,
                                     block: Boolean = true, fence: Long? = null) {
    if (this.address() == NULL) {
        return
    }

    if (vkEndCommandBuffer(this) != VK_SUCCESS) {
        throw AssertionError("Failed to end command buffer $this")
    }

    if (flush) {
        this.submit(queue, submitInfoPNext, waitSemaphores = waitSemaphores, signalSemaphores = signalSemaphores, waitDstStageMask = waitDstStageMask, block = block, fence = fence)
    }

    if (dealloc) {
        vkFreeCommandBuffers(device.vulkanDevice, commandPool, this)
    }
}

/**
 * Submits the given command buffer to the queue [queue].
 *
 * [submitInfoPNext], [signalSemaphores], [waitSemaphores] and [waitDstStageMask] can be used to further fine-grain
 * the submission process.
 */
fun VkCommandBuffer.submit(queue: VulkanDevice.QueueWithMutex, submitInfoPNext: Pointer? = null,
                           signalSemaphores: LongBuffer? = null, waitSemaphores: LongBuffer? = null,
                           waitDstStageMask: IntBuffer? = null,
                           block: Boolean = true, fence: Long? = null) {
    stackPush().use { stack ->
        val submitInfo = VkSubmitInfo.calloc(1, stack)
        val commandBuffers = stack.callocPointer(1).put(0, this)

        VU.run("VkCommandBuffer.submit", {
            submitInfo
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(commandBuffers)
                .pSignalSemaphores(signalSemaphores)
                .pNext(submitInfoPNext?.address() ?: NULL)

            if((waitSemaphores?.remaining() ?: 0) > 0
                && waitSemaphores != null
                && waitDstStageMask != null) {
                submitInfo
                    .waitSemaphoreCount(waitSemaphores.remaining())
                    .pWaitSemaphores(waitSemaphores)
                    .pWaitDstStageMask(waitDstStageMask)
            }

            if(block) {
                queue.mutex.acquire()
                vkQueueSubmit(queue.queue, submitInfo, fence ?: VK_NULL_HANDLE)
                val r = vkQueueWaitIdle(queue.queue)
                queue.mutex.release()
                r
            } else {
                queue.mutex.acquire()
                val r = vkQueueSubmit(queue.queue, submitInfo, fence ?: VK_NULL_HANDLE)
                queue.mutex.release()
                r
            }
        }, { })
    }
}

/**
 * Converts a [Blending.BlendFactor] to a Vulkan-internal integer-based descriptor.
 */
fun Blending.BlendFactor.toVulkan() = when (this) {
    Blending.BlendFactor.Zero -> VK_BLEND_FACTOR_ZERO
    Blending.BlendFactor.One -> VK_BLEND_FACTOR_ONE

    Blending.BlendFactor.SrcAlpha -> VK_BLEND_FACTOR_SRC_ALPHA
    Blending.BlendFactor.OneMinusSrcAlpha -> VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA

    Blending.BlendFactor.SrcColor -> VK_BLEND_FACTOR_SRC_COLOR
    Blending.BlendFactor.OneMinusSrcColor -> VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR

    Blending.BlendFactor.DstColor -> VK_BLEND_FACTOR_DST_COLOR
    Blending.BlendFactor.OneMinusDstColor -> VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR

    Blending.BlendFactor.DstAlpha -> VK_BLEND_FACTOR_DST_ALPHA
    Blending.BlendFactor.OneMinusDstAlpha -> VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA

    Blending.BlendFactor.ConstantColor -> VK_BLEND_FACTOR_CONSTANT_COLOR
    Blending.BlendFactor.OneMinusConstantColor -> VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR

    Blending.BlendFactor.ConstantAlpha -> VK_BLEND_FACTOR_CONSTANT_ALPHA
    Blending.BlendFactor.OneMinusConstantAlpha -> VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA

    Blending.BlendFactor.Src1Color -> VK_BLEND_FACTOR_SRC1_COLOR
    Blending.BlendFactor.OneMinusSrc1Color -> VK_BLEND_FACTOR_ONE_MINUS_SRC1_COLOR

    Blending.BlendFactor.Src1Alpha -> VK_BLEND_FACTOR_SRC1_ALPHA
    Blending.BlendFactor.OneMinusSrc1Alpha -> VK_BLEND_FACTOR_ONE_MINUS_SRC1_ALPHA

    Blending.BlendFactor.SrcAlphaSaturate -> VK_BLEND_FACTOR_SRC_ALPHA_SATURATE
}

/**
 * Converts a [Blending.BlendOp] to a Vulkan-intenal integer-based descriptor.
 */
fun Blending.BlendOp.toVulkan() = when (this) {
    Blending.BlendOp.add -> VK_BLEND_OP_ADD
    Blending.BlendOp.subtract -> VK_BLEND_OP_SUBTRACT
    Blending.BlendOp.min -> VK_BLEND_OP_MIN
    Blending.BlendOp.max -> VK_BLEND_OP_MAX
    Blending.BlendOp.reverse_subtract -> VK_BLEND_OP_REVERSE_SUBTRACT
}

/**
 * VU - Vulkan Utils
 *
 * A collection of convenience methods for various Vulkan-related tasks
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class VU {

    /**
     * Exception class to be thrown when a Vulkan command fails.
     */
    class VulkanCommandException(message: String) : RuntimeException(message)

    /**
     * Companion object for [VU] to access methods statically.
     */
    companion object {
        private val logger by lazyLogger()

        /**
         * Runs a lambda [function] containing a Vulkan call, and checks it for errors. Allowed error codes
         * can be set in [allowedResults], for debugging, the call may be given a [name].
         */
        inline fun run(name: String, function: () -> Int, allowedResults: List<Int> = emptyList()) {
            val result = function.invoke()

            if (result != VK_SUCCESS && result !in allowedResults) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")

                if(result < 0) {
                   throw VulkanCommandException("Call to $name failed: ${translate(result)}")
                }
            }

            if(result in allowedResults) {
                LoggerFactory.getLogger("VulkanRenderer").debug("Call to $name did not result in error because return code(s) ${allowedResults.joinToString(", ")} were explicitly tolerated.")
            }
        }

        /**
         * Runs a lambda [function] containing a Vulkan call, and checks it for errors. After the call, [cleanup] is run.
         * For debugging, the call may be given a [name].
         */
        inline fun run(name: String, function: () -> Int, cleanup: () -> Any) {
            val result = function.invoke()

            if (result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")

                if(result < 0) {
                    throw VulkanCommandException("Call to $name failed: ${translate(result)}")
                }
            }

            cleanup.invoke()
        }

        /**
         * Runs a lambda [function] containing a Vulkan call, and checks it for errors. The Vulkan call will return
         * an int, which is in turn returned by this function.
         * For debugging, the call may be given a [name].
         */
        inline fun getInt(name: String, function: IntBuffer.() -> Int): Int {
            return stackPush().use { stack ->
                val receiver = stack.callocInt(2)
                val result = function.invoke(receiver)

                if (result != VK_SUCCESS) {
                    LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")

                    if(result < 0) {
                        throw VulkanCommandException("Call to $name failed: ${translate(result)}")
                    }
                }

                val ret = receiver.get(0)

                ret
            }
        }

        /**
         * Runs a lambda [function] containing a Vulkan call, and checks it for errors. The Vulkan call will return
         * an [IntBuffer], containing [count] elements, which is in turn returned by this function.
         * For debugging, the call may be given a [name].
         */
        inline fun getInts(name: String, count: Int, function: IntBuffer.() -> Int): IntBuffer {
            val receiver = memAllocInt(count)
            val result = function.invoke(receiver)

            if (result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")

                if(result < 0) {
                    throw VulkanCommandException("Call to $name failed: ${translate(result)}")
                }
            }

            return receiver
        }

        /**
         * Runs a lambda [function] containing a Vulkan call, and checks it for errors. The Vulkan call will return
         * a long, which is in turn returned by this function. After the function has been run, [cleanup] is called.
         * For debugging, the call may be given a [name].
         */
        inline fun getLong(name: String, function: LongBuffer.() -> Int, cleanup: LongBuffer.() -> Any): Long {
            return stackPush().use { stack ->
                val receiver = stack.callocLong(1)
                val result = function.invoke(receiver)

                if (result != VK_SUCCESS) {
                    LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
                    cleanup.invoke(receiver)

                    if(result < 0) {
                        throw VulkanCommandException("Call to $name failed: ${translate(result)}")
                    }
                }

                val ret = receiver.get(0)
                cleanup.invoke(receiver)

                ret
            }
        }

        /**
         * Runs a lambda [function] containing a Vulkan call, and checks it for errors. The Vulkan call will return
         * an [LongBuffer], containing [count] elements, which is in turn returned by this function. After running the
         * function, [cleanup] is called.
         * For debugging, the call may be given a [name].
         */
        inline fun getLongs(name: String, count: Int, function: LongBuffer.() -> Int, cleanup: LongBuffer.() -> Any): LongBuffer {
                val receiver = memAllocLong(count)
                val result = function.invoke(receiver)

                if (result != VK_SUCCESS) {
                    LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
                    cleanup.invoke(receiver)

                    if(result < 0) {
                        throw VulkanCommandException("Call to $name failed: ${translate(result)}")
                    }
                }

                cleanup.invoke(receiver)
            return receiver
        }

        /**
         * Runs a lambda [function] containing a Vulkan call, and checks it for errors. The Vulkan call will return
         * a pointer, which is in turn returned by this function as a long. After running the function, [cleanup] is called.
         * For debugging, the call may be given a [name].
         */
        inline fun getPointer(name: String, function: PointerBuffer.() -> Int, cleanup: PointerBuffer.() -> Any): Long {
            return stackPush().use { stack ->
                val receiver = stack.callocPointer(1)
                val result = function.invoke(receiver)

                if (result != VK_SUCCESS) {
                    LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")

                    if(result < 0) {
                        throw VulkanCommandException("Call to $name failed: ${translate(result)}")
                    }
                }

                cleanup.invoke(receiver)

                receiver.get(0)
            }
        }

        /**
         * Runs a lambda [function] containing a Vulkan call, and checks it for errors. The Vulkan call will return
         * a [PointerBuffer], containing [count] elements, which is in turn returned by this function.
         * For debugging, the call may be given a [name].
         */
        inline fun getPointers(name: String, count: Int, function: PointerBuffer.() -> Int): PointerBuffer {
            val receiver = memAllocPointer(count)
            val result = function.invoke(receiver)

            if(result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")

                if(result < 0) {
                    throw VulkanCommandException("Call to $name failed: ${translate(result)}")
                }
            }

            return receiver
        }

        /**
         * Runs a lambda [function] containing a Vulkan call, and checks it for errors. The Vulkan call will return
         * a [PointerBuffer], containing [count] elements, which is in turn returned by this function. After running
         * the function, [cleanup] is run.
         * For debugging, the call may be given a [name].
         */
        inline fun getPointers(name: String, count: Int, function: PointerBuffer.() -> Int, cleanup: PointerBuffer.() -> Any): PointerBuffer {
            val receiver = memAllocPointer(count)
            val result = function.invoke(receiver)

            if(result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
                cleanup.invoke(receiver)

                if(result < 0) {
                    throw VulkanCommandException("Call to $name failed: ${translate(result)}")
                }
            }

            cleanup.invoke(receiver)

            return receiver
        }

        /**
         * Translates a Vulkan `VkResult` value given as [result] to a String describing the result.
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

        /**
         * Transforms a Vulkan [image] from the old image layout [oldImageLayout] to the new [newImageLayout], taking into account the
         * [VkImageSubresourceRange] given in [range]. This function can only be run within a [commandBuffer].
         */
        fun setImageLayout(commandBuffer: VkCommandBuffer, image: Long, oldImageLayout: Int, newImageLayout: Int, range: VkImageSubresourceRange) {
            stackPush().use { stack ->
                val imageMemoryBarrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .pNext(NULL)
                    .oldLayout(oldImageLayout)
                    .newLayout(newImageLayout)
                    .image(image)
                    .subresourceRange(range)
                    .srcAccessMask(when (oldImageLayout) {
                        VK_IMAGE_LAYOUT_PREINITIALIZED -> VK_ACCESS_HOST_WRITE_BIT
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> VK_ACCESS_TRANSFER_READ_BIT
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK_ACCESS_TRANSFER_WRITE_BIT
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> VK_ACCESS_SHADER_READ_BIT

                        VK_IMAGE_LAYOUT_UNDEFINED -> 0
                        else -> 0
                    })

                when (newImageLayout) {
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
                        if (imageMemoryBarrier.srcAccessMask() == 0) {
                            imageMemoryBarrier.dstAccessMask(VK_ACCESS_HOST_WRITE_BIT or VK_ACCESS_TRANSFER_WRITE_BIT)
                        }

                        imageMemoryBarrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    }
                    VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> {
                        imageMemoryBarrier.dstAccessMask(imageMemoryBarrier.dstAccessMask() or VK_ACCESS_MEMORY_READ_BIT)
                    }
                }

                val srcStageFlags = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                val dstStageFlags = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT

                vkCmdPipelineBarrier(commandBuffer,
                    srcStageFlags,
                    dstStageFlags,
                    0,
                    null,
                    null,
                    imageMemoryBarrier
                )
            }
        }

        /**
         * Transforms a Vulkan [image] from the old image layout [oldImageLayout] to the new [newImageLayout], the
         * [VkImageSubresourceRange] is constructed based on the image size, and only uses the base MIP level and array layer.
         * This function can only be run within a [commandBuffer].
         */
        fun setImageLayout(commandBuffer: VkCommandBuffer, image: Long, aspectMask: Int, oldImageLayout: Int, newImageLayout: Int) {
            stackPush().use { stack ->
                val range = VkImageSubresourceRange.calloc(stack)
                    .aspectMask(aspectMask)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .layerCount(1)

                setImageLayout(commandBuffer, image, oldImageLayout, newImageLayout, range)
            }
        }

        /**
         * Creates and returns a new command buffer on [device], associated with [commandPool]. By default, it'll be a primary
         * command buffer, that can be changed by setting [level] to [VK_COMMAND_BUFFER_LEVEL_SECONDARY].
         */
        fun newCommandBuffer(device: VulkanDevice, commandPool: Long, level: Int = VK_COMMAND_BUFFER_LEVEL_PRIMARY): VkCommandBuffer {
            return stackPush().use { stack ->
                val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(level)
                    .commandBufferCount(1)

                val commandBuffer = getPointer("Creating command buffer",
                    { vkAllocateCommandBuffers(device.vulkanDevice, cmdBufAllocateInfo, this) }, {})

                VkCommandBuffer(commandBuffer, device.vulkanDevice)
            }
        }

        /**
         * Creates and returns a new command buffer on [device], associated with [commandPool]. By default, it'll be a primary
         * command buffer, that can be changed by setting [level] to [VK_COMMAND_BUFFER_LEVEL_SECONDARY]. If recording should
         * be started automatically, set [autostart] to true.
         */
        fun newCommandBuffer(device: VulkanDevice, commandPool: Long, level: Int = VK_COMMAND_BUFFER_LEVEL_PRIMARY, autostart: Boolean = false): VkCommandBuffer {
            val cmdBuf = newCommandBuffer(device, commandPool, level)

            if(autostart) {
               beginCommandBuffer(cmdBuf)
            }

            return cmdBuf
        }

        /**
         * Starts recording of [commandBuffer]. Usually called from [newCommandBuffer]. Additional [flags] may be set,
         * e.g. for creating resettable or simultaneous use buffer.
         */
        fun beginCommandBuffer(commandBuffer: VkCommandBuffer, flags: Int = VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT) {
            stackPush().use { stack ->
                val cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .pNext(NULL)
                    .flags(flags)

                VU.run("Beginning command buffer", { vkBeginCommandBuffer(commandBuffer, cmdBufInfo) }, {})
            }
        }

        /**
         * Updates a given _dynamic_ [descriptorSet] to use [buffer] from now on.
         */
        fun updateDynamicDescriptorSetBuffer(device: VulkanDevice, descriptorSet: Long,
                                       bindingCount: Int, buffer: VulkanBuffer): Long {
            logger.trace("Updating dynamic descriptor set with {} bindings to use buffer {}", bindingCount, buffer)

            return stackPush().use { stack ->
                val d = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(buffer.vulkanBuffer)
                    .range(2048)
                    .offset(0L)

                val writeDescriptorSet = VkWriteDescriptorSet.calloc(bindingCount, stack)

                (0 until bindingCount).forEach { i ->
                    writeDescriptorSet[i]
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .pNext(NULL)
                        .dstSet(descriptorSet)
                        .dstBinding(i)
                        .dstArrayElement(0)
                        .pBufferInfo(d)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                        .descriptorCount(1)
                }

                vkUpdateDescriptorSets(device.vulkanDevice, writeDescriptorSet, null)

                descriptorSet
            }
        }
    }
}
