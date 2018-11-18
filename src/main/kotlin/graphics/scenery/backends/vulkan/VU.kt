package graphics.scenery.backends.vulkan

import graphics.scenery.Blending
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.vulkan.VulkanDevice.Companion.logger
import graphics.scenery.utils.LazyLogger
import kool.stak
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT
import org.lwjgl.vulkan.KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR
import org.lwjgl.vulkan.KHRSurface.VK_ERROR_NATIVE_WINDOW_IN_USE_KHR
import org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.LoggerFactory
import vkk.*
import vkk.`object`.*
import java.math.BigInteger
import java.nio.IntBuffer
import java.nio.LongBuffer

/**
 * Returns a given Long as hex-formatted string.
 *
 * @returns The long value as hex string.
 */
fun Long.toHexString(): String {
    return String.format("0x%X", this)
}

/**
 * Returns a given BigInteger as hex-formatted string.
 *
 * @returns The BigInteger value as hex string.
 */
fun BigInteger.toHexString(): String {
    return "0x${this.toString(16)}"
}

/**
 * Creates and returns a new command buffer on [device], associated with [commandPool]. By default, it'll be a primary
 * command buffer, that can be changed by setting [level] to [VK_COMMAND_BUFFER_LEVEL_SECONDARY].
 */
fun VkDevice.newCommandBuffer(commandPool: VkCommandPool, level: VkCommandBufferLevel = VkCommandBufferLevel.PRIMARY): Pair<VkCommandBuffer, VkCommandPool> {

    val cmdBufAllocateInfo = vk.CommandBufferAllocateInfo {
        this.commandPool = commandPool
        this.level = level
        commandBufferCount = 1
    }
    return allocateCommandBuffer(cmdBufAllocateInfo) to commandPool
}

/** begin .. end */
inline fun Pair<VkCommandBuffer, VkCommandPool>.record(block: VkCommandBuffer.() -> Unit): Pair<VkCommandBuffer, VkCommandPool> {

    val cmdBufInfo = vk.CommandBufferBeginInfo { flags = VkCommandBufferUsage.SIMULTANEOUS_USE_BIT.i }

    first.record(cmdBufInfo, block)

    return this
}

fun Pair<VkCommandBuffer, VkCommandPool>.deallocate() = first.device.freeCommandBuffer(second, first)

/**
 * Ends the recording of a command buffer.
 */
fun VkCommandBuffer.endCommandBuffer() {
    if (vkEndCommandBuffer(this) != VK_SUCCESS) {
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
                                     queue: VkQueue?, flush: Boolean = true,
                                     dealloc: Boolean = false,
                                     submitInfoPNext: Pointer? = null,
                                     signalSemaphores: VkSemaphoreBuffer? = null, waitSemaphores: VkSemaphoreBuffer? = null,
                                     waitDstStageMask: IntBuffer? = null,
                                     block: Boolean = true) {
    if (this.address() == NULL) {
        return
    }

    if (vkEndCommandBuffer(this) != VK_SUCCESS) {
        throw AssertionError("Failed to end command buffer $this")
    }

    if (flush && queue != null) {
        this.submit(queue, submitInfoPNext, waitSemaphores = waitSemaphores, signalSemaphores = signalSemaphores, waitDstStageMask = waitDstStageMask, block = block)
    }

    if (dealloc) {
        vkFreeCommandBuffers(device.vulkanDevice, commandPool, this)
    }
}

fun Pair<VkCommandBuffer, VkCommandPool>.submit(queue: VkQueue, submitInfoPNext: Pointer? = null,
                                                signalSemaphores: VkSemaphoreBuffer? = null, waitSemaphores: VkSemaphoreBuffer? = null,
                                                waitDstStageMask: IntBuffer? = null,
                                                block: Boolean = true): Pair<VkCommandBuffer, VkCommandPool> =
    apply { first.submit(queue, submitInfoPNext, signalSemaphores, waitSemaphores, waitDstStageMask, block) }

/**
 * Submits the given command buffer to the queue [queue].
 *
 * [submitInfoPNext], [signalSemaphores], [waitSemaphores] and [waitDstStageMask] can be used to further fine-grain
 * the submission process.
 */
fun VkCommandBuffer.submit(queue: VkQueue, submitInfoPNext: Pointer? = null,
                           signalSemaphores: VkSemaphoreBuffer? = null, waitSemaphores: VkSemaphoreBuffer? = null,
                           waitDstStageMask: IntBuffer? = null,
                           block: Boolean = true): VkCommandBuffer {

    val submitInfo = vk.SubmitInfo().also {
        it.commandBuffer = this@submit
        it.signalSemaphores = signalSemaphores
        it.next = submitInfoPNext?.adr ?: NULL

        if (waitSemaphores?.isNotEmpty() == true && waitDstStageMask != null) {
            it.waitSemaphoreCount = waitSemaphores.rem
            it.waitSemaphores = waitSemaphores
            it.waitDstStageMask = waitDstStageMask
        }
    }

    queue submit submitInfo
    if (block)
        queue.waitIdle()

    return this
}

/**
 * Converts a [Blending.BlendFactor] to a Vulkan-internal integer-based descriptor.
 */
fun Blending.BlendFactor.toVulkan() = when (this) {
    Blending.BlendFactor.Zero -> VkBlendFactor.ZERO
    Blending.BlendFactor.One -> VkBlendFactor.ONE

    Blending.BlendFactor.SrcAlpha -> VkBlendFactor.SRC_ALPHA
    Blending.BlendFactor.OneMinusSrcAlpha -> VkBlendFactor.ONE_MINUS_SRC_ALPHA

    Blending.BlendFactor.SrcColor -> VkBlendFactor.SRC_COLOR
    Blending.BlendFactor.OneMinusSrcColor -> VkBlendFactor.ONE_MINUS_SRC_COLOR

    Blending.BlendFactor.DstColor -> VkBlendFactor.DST_COLOR
    Blending.BlendFactor.OneMinusDstColor -> VkBlendFactor.ONE_MINUS_DST_COLOR

    Blending.BlendFactor.DstAlpha -> VkBlendFactor.DST_ALPHA
    Blending.BlendFactor.OneMinusDstAlpha -> VkBlendFactor.ONE_MINUS_DST_ALPHA

    Blending.BlendFactor.ConstantColor -> VkBlendFactor.CONSTANT_COLOR
    Blending.BlendFactor.OneMinusConstantColor -> VkBlendFactor.ONE_MINUS_CONSTANT_COLOR

    Blending.BlendFactor.ConstantAlpha -> VkBlendFactor.CONSTANT_ALPHA
    Blending.BlendFactor.OneMinusConstantAlpha -> VkBlendFactor.ONE_MINUS_CONSTANT_ALPHA

    Blending.BlendFactor.Src1Color -> VkBlendFactor.SRC1_COLOR
    Blending.BlendFactor.OneMinusSrc1Color -> VkBlendFactor.ONE_MINUS_SRC1_COLOR

    Blending.BlendFactor.Src1Alpha -> VkBlendFactor.SRC1_ALPHA
    Blending.BlendFactor.OneMinusSrc1Alpha -> VkBlendFactor.ONE_MINUS_SRC1_ALPHA

    Blending.BlendFactor.SrcAlphaSaturate -> VkBlendFactor.SRC_ALPHA_SATURATE
}

/**
 * Converts a [Blending.BlendOp] to a Vulkan-intenal integer-based descriptor.
 */
fun Blending.BlendOp.toVulkan() = when (this) {
    Blending.BlendOp.add -> VkBlendOp.ADD
    Blending.BlendOp.subtract -> VkBlendOp.SUBTRACT
    Blending.BlendOp.min -> VkBlendOp.MIN
    Blending.BlendOp.max -> VkBlendOp.MAX
    Blending.BlendOp.reverse_subtract -> VkBlendOp.REVERSE_SUBTRACT
}

/**
 * Creates and returns a new descriptor set layout on [vkDev] with the members declared in [type], which is
 * a [List] of a Pair of a type, associated with a count (e.g. Dynamic UBO to 1). The base binding can be set with [binding].
 * The shader stages to which the DSL should be visible can be set via [shaderStages].
 */
fun VkDevice.createDescriptorSetLayout(type: Pair<VkDescriptorType, Int>, binding: Int = 0, shaderStages: VkShaderStageFlags = VkShaderStage.ALL.i): VkDescriptorSetLayout =
    createDescriptorSetLayout(listOf(type), binding, shaderStages)
/**
 * Creates and returns a new descriptor set layout on [vkDev] with the members declared in [types], which is
 * a [List] of a Pair of a type, associated with a count (e.g. Dynamic UBO to 1). The base binding can be set with [binding].
 * The shader stages to which the DSL should be visible can be set via [shaderStages].
 */
fun VkDevice.createDescriptorSetLayout(types: List<Pair<VkDescriptorType, Int>>, binding: Int = 0, shaderStages: VkShaderStageFlags = VkShaderStage.ALL.i): VkDescriptorSetLayout =
    VU.createDescriptorSetLayout(this, types, binding, shaderStages)

/**
 * Creates and returns a dynamic descriptor set allocated on [vkDev] from the pool [descriptorPool], conforming
 * to the existing descriptor set layout [descriptorSetLayout]. The number of bindings ([bindingCount]) and the
 * associated [buffer] have to be given.
 */
fun VkDevice.createDescriptorSetDynamic(descriptorPool: VkDescriptorPool, descriptorSetLayout: VkDescriptorSetLayout,
                               bindingCount: Int, buffer: VulkanBuffer): VkDescriptorSet =
    VU.createDescriptorSetDynamic(this, descriptorPool, descriptorSetLayout, bindingCount, buffer)

/**
 * VU - Vulkan Utils
 *
 * A collection of convenience methods for various Vulkan-related tasks
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class VU {

    /**
     * Companion object for [VU] to access methods statically.
     */
    companion object {
        private val logger by LazyLogger()

        /**
         * Runs a lambda [function] containing a Vulkan call, and checks it for errors. Allowed error codes
         * can be set in [allowedResults], for debugging, the call may be given a [name].
         */
        inline fun run(name: String, function: () -> Int, allowedResults: List<Int> = emptyList()) {
            val result = function.invoke()

            if (result != VK_SUCCESS && result !in allowedResults) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")

                if (result < 0) {
                    throw RuntimeException("Call to $name failed: ${translate(result)}")
                }
            }

            if (result in allowedResults) {
                LoggerFactory.getLogger("VulkanRenderer").debug("Call to $name did not result in error because return code(s) ${allowedResults.joinToString()} were explicitly tolerated.")
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

                if (result < 0) {
                    throw RuntimeException("Call to $name failed: ${translate(result)}")
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

                    if (result < 0) {
                        throw RuntimeException("Call to $name failed: ${translate(result)}")
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

                if (result < 0) {
                    throw RuntimeException("Call to $name failed: ${translate(result)}")
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

                    if (result < 0) {
                        throw RuntimeException("Call to $name failed: ${translate(result)}")
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

                if (result < 0) {
                    throw RuntimeException("Call to $name failed: ${translate(result)}")
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

                    if (result < 0) {
                        throw RuntimeException("Call to $name failed: ${translate(result)}")
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

            if (result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")

                if (result < 0) {
                    throw RuntimeException("Call to $name failed: ${translate(result)}")
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

            if (result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
                cleanup.invoke(receiver)

                if (result < 0) {
                    throw RuntimeException("Call to $name failed: ${translate(result)}")
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
                val imageMemoryBarrier = VkImageMemoryBarrier.callocStack(1, stack)
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
        fun setImageLayout(commandBuffer: VkCommandBuffer, image: VkImage, aspectMask: VkImageAspectFlags, oldImageLayout: VkImageLayout, newImageLayout: VkImageLayout) {
            stackPush().use { stack ->
                val range = VkImageSubresourceRange.callocStack(stack)
                    .aspectMask(aspectMask)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .layerCount(1)

                setImageLayout(commandBuffer, image.L, oldImageLayout.i, newImageLayout.i, range)
            }
        }

        /**
         * Creates a new Vulkan queue on [device] with the queue family index [queueFamilyIndex] and returns the queue.
         */
        fun createDeviceQueue(device: VulkanDevice, queueFamilyIndex: Int): VkQueue {
            val queue = getPointer("Getting device queue for queueFamilyIndex=$queueFamilyIndex",
                { vkGetDeviceQueue(device.vulkanDevice, queueFamilyIndex, 0, this); VK_SUCCESS }, {})

            return VkQueue(queue, device.vulkanDevice)
        }

        /**
         * Creates and returns a new command buffer on [device], associated with [commandPool]. By default, it'll be a primary
         * command buffer, that can be changed by setting [level] to [VK_COMMAND_BUFFER_LEVEL_SECONDARY]. If recording should
         * be started automatically, set [autostart] to true.
         */
        fun newCommandBuffer(vkDev: VkDevice, commandPool: VkCommandPool, level: VkCommandBufferLevel = VkCommandBufferLevel.PRIMARY, autostart: Boolean = false): VkCommandBuffer {

            val cmdBufAllocateInfo = vk.CommandBufferAllocateInfo().also {
                it.commandPool = commandPool
                it.level = level
                it.commandBufferCount =1
            }
            val commandBuffer = vkDev allocateCommandBuffer cmdBufAllocateInfo

            if (autostart) {
                beginCommandBuffer(commandBuffer)
            }

            return commandBuffer
        }

        /**
         * Starts recording of [commandBuffer]. Usually called from [newCommandBuffer]. Additional [flags] may be set,
         * e.g. for creating resettable or simultaneous use buffer.
         */
        fun beginCommandBuffer(commandBuffer: VkCommandBuffer, flags: Int = VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT) {
            stackPush().use { stack ->
                val cmdBufInfo = VkCommandBufferBeginInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .pNext(NULL)
                    .flags(flags)

                VU.run("Beginning command buffer", { vkBeginCommandBuffer(commandBuffer, cmdBufInfo) }, {})
            }
        }

        /**
         * Creates and returns a new descriptor set layout on [device] with one member of [type], which is by default a
         * dynamic uniform buffer. The [binding] and number of descriptors ([descriptorNum], [descriptorCount]) can be
         * customized,  as well as the shader stages to which the DSL should be visible ([shaderStages]).
         */
        fun createDescriptorSetLayout(vkDev: VkDevice, type: VkDescriptorType = VkDescriptorType.UNIFORM_BUFFER_DYNAMIC,
                                      binding: Int = 0, descriptorNum: Int = 1, descriptorCount: Int = 1,
                                      shaderStages: VkShaderStageFlags = VkShaderStage.ALL.i): VkDescriptorSetLayout {

            val layoutBinding = vk.DescriptorSetLayoutBinding(descriptorNum)
            (binding until descriptorNum).forEach { i ->
                layoutBinding[i].apply {
                    this.binding = i
                    descriptorType = type
                    this.descriptorCount = descriptorCount
                    stageFlags = shaderStages
                }
            }

            val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(layoutBinding)

            return vkDev.createDescriptorSetLayout(descriptorLayout).also {
                logger.debug("Created DSL ${it.asHexString} with $descriptorNum descriptors with $descriptorCount elements.")
            }
        }

        /**
         * Creates and returns a new descriptor set layout on [vkDev] with the members declared in [types], which is
         * a [List] of a Pair of a type, associated with a count (e.g. Dynamic UBO to 1). The base binding can be set with [binding].
         * The shader stages to which the DSL should be visible can be set via [shaderStages].
         */
        fun createDescriptorSetLayout(vkDev: VkDevice, types: List<Pair<VkDescriptorType, Int>>, binding: Int = 0, shaderStages: VkShaderStageFlags = VkShaderStage.ALL.i): VkDescriptorSetLayout {

            val layoutBinding = vk.DescriptorSetLayoutBinding(types.size)

            types.forEachIndexed { i, (type, count) ->
                layoutBinding[i].apply {
                    this.binding = i + binding
                    descriptorType = type
                    descriptorCount = count
                    stageFlags = shaderStages
                }
            }

            val descriptorLayout = vk.DescriptorSetLayoutCreateInfo { bindings = layoutBinding }

            return vkDev.createDescriptorSetLayout(descriptorLayout).also {
                logger.debug("Created DSL ${it.asHexString} with ${types.size} descriptors.")
            }
        }

        /**
         * Creates and returns a dynamic descriptor set allocated on [vkDev] from the pool [descriptorPool], conforming
         * to the existing descriptor set layout [descriptorSetLayout]. The number of bindings ([bindingCount]) and the
         * associated [buffer] have to be given.
         */
        fun createDescriptorSetDynamic(vkDev: VkDevice, descriptorPool: VkDescriptorPool, descriptorSetLayout: VkDescriptorSetLayout,
                                       bindingCount: Int, buffer: VulkanBuffer): VkDescriptorSet {
            logger.debug("Creating dynamic descriptor set with $bindingCount bindings, DSL=${descriptorSetLayout.asHexString}")

            val allocInfo = vk.DescriptorSetAllocateInfo {
                this.descriptorPool = descriptorPool
                setLayout = descriptorSetLayout
            }
            val descriptorSet = vkDev allocateDescriptorSets allocInfo

            val d = vk.DescriptorBufferInfo {
                this.buffer = buffer.vulkanBuffer
                this.range = VkDeviceSize(2048)
                this.offset = VkDeviceSize(0)
            }
            val writeDescriptorSet = vk.WriteDescriptorSet(bindingCount)

            (0 until bindingCount).forEach { i ->
                writeDescriptorSet[i].apply {
                    dstSet = descriptorSet
                    dstBinding = i
                    dstArrayElement = 0
                    bufferInfo_ = d
                    descriptorType = VkDescriptorType.UNIFORM_BUFFER_DYNAMIC
                }
            }

            vkDev updateDescriptorSets writeDescriptorSet

            return descriptorSet
        }

        /**
         * Updates a given _dynamic_ [descriptorSet] to use [buffer] from now on.
         */
        fun updateDynamicDescriptorSetBuffer(device: VulkanDevice, descriptorSet: Long,
                                             bindingCount: Int, buffer: VulkanBuffer): Long {
            logger.trace("Updating dynamic descriptor set with {} bindings to use buffer {}", bindingCount, buffer)

            return stackPush().use { stack ->
                val d = VkDescriptorBufferInfo.callocStack(1, stack)
                    .buffer(buffer.vulkanBuffer.L)
                    .range(2048)
                    .offset(0L)

                val writeDescriptorSet = VkWriteDescriptorSet.callocStack(bindingCount, stack)

                (0 until bindingCount).forEach { i ->
                    writeDescriptorSet[i]
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .pNext(NULL)
                        .dstSet(descriptorSet)
                        .dstBinding(i)
                        .dstArrayElement(0)
                        .pBufferInfo(d)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                }

                vkUpdateDescriptorSets(device.vulkanDevice, writeDescriptorSet, null)

                descriptorSet
            }
        }

        /**
         * Creates and returns a new descriptor set, allocated on [device] from [descriptorPool], conforming to existing
         * [descriptorSetLayout], a [bindingCount] needs to be given as well an an [ubo] to back the descriptor set.
         * The default [type] is [VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER].
         */
        fun createDescriptorSet(vkDev: VkDevice, descriptorPool: VkDescriptorPool, descriptorSetLayout: VkDescriptorSetLayout, bindingCount: Int,
                                ubo: VulkanUBO.UBODescriptor, type: VkDescriptorType = VkDescriptorType.UNIFORM_BUFFER): VkDescriptorSet {
            logger.debug("Creating descriptor set with ${bindingCount} bindings, DSL=$descriptorSetLayout")
            return stak { stack ->
                val pDescriptorSetLayout = stack.callocLong(1).put(0, descriptorSetLayout.L)

                val allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .pNext(NULL)
                    .descriptorPool(descriptorPool.L)
                    .pSetLayouts(pDescriptorSetLayout)

                val descriptorSet = getLong("createDescriptorSet",
                    { vkAllocateDescriptorSets(vkDev, allocInfo, this) }, {})

                val d =
                    VkDescriptorBufferInfo.callocStack(1, stack)
                        .buffer(ubo.buffer.L)
                        .range(ubo.range)
                        .offset(ubo.offset)

                val writeDescriptorSet = VkWriteDescriptorSet.callocStack(bindingCount, stack)

                (0 until bindingCount).forEach { i ->
                    writeDescriptorSet[i]
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .pNext(NULL)
                        .dstSet(descriptorSet)
                        .dstBinding(i)
                        .pBufferInfo(d)
                        .descriptorType(type.i)
                }

                vkUpdateDescriptorSets(vkDev, writeDescriptorSet, null)

                VkDescriptorSet(descriptorSet)
            }
        }

        /**
         * Creates and returns a new descriptor set for a framebuffer given as [target]. The set will be allocated on [device],
         * from [descriptorPool], and conforms to an existing descriptor set layout [descriptorSetLayout]. Additional
         * metadata about the framebuffer needs to be given via [rt], and a subset of the framebuffer can be selected
         * by setting [onlyFor] to the respective name of the attachment.
         */
        fun createRenderTargetDescriptorSet(device: VulkanDevice, descriptorPool: VkDescriptorPool, descriptorSetLayout: VkDescriptorSetLayout,
                                            rt: Map<String, RenderConfigReader.TargetFormat>,
                                            target: VulkanFramebuffer, onlyFor: String? = null): Long {

            return stackPush().use { stack ->
                val pDescriptorSetLayout = stack.callocLong(1).put(0, descriptorSetLayout.L)

                val allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .pNext(NULL)
                    .descriptorPool(descriptorPool.L)
                    .pSetLayouts(pDescriptorSetLayout)

                val descriptorSet = getLong("createDescriptorSet",
                    { vkAllocateDescriptorSets(device.vulkanDevice, allocInfo, this) }, {})

                val descriptorWrites = if (onlyFor == null) {
                    val writeDescriptorSet = VkWriteDescriptorSet.callocStack(rt.size, stack)

                    rt.entries.forEachIndexed { i, entry ->
                        val attachment = target.attachments[entry.key]!!
                        val d = VkDescriptorImageInfo.callocStack(1, stack)

                        d
                            .imageView(attachment.imageView.L)
                            .sampler(target.framebufferSampler.L)
                            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

                        writeDescriptorSet[i]
                            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                            .pNext(NULL)
                            .dstSet(descriptorSet)
                            .dstBinding(i)
                            .dstArrayElement(0)
                            .pImageInfo(d)
                            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    }
                    writeDescriptorSet
                } else {
                    val writeDescriptorSet = VkWriteDescriptorSet.callocStack(1, stack)

                    rt.entries.first { it.key == onlyFor }.apply {
                        val attachment = target.attachments[this.key]!!
                        val d = VkDescriptorImageInfo.callocStack(1, stack)

                        d
                            .imageView(attachment.imageView.L)
                            .sampler(target.framebufferSampler.L)
                            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

                        writeDescriptorSet[0]
                            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                            .pNext(NULL)
                            .dstSet(descriptorSet)
                            .dstBinding(0)
                            .dstArrayElement(0)
                            .pImageInfo(d)
                            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    }
                    writeDescriptorSet
                }

                vkUpdateDescriptorSets(device.vulkanDevice, descriptorWrites, null)

                logger.debug("Creating framebuffer attachment descriptor $descriptorSet set with ${rt.size} bindings, DSL=$descriptorSetLayout")
                descriptorSet
            }
        }
    }
}
