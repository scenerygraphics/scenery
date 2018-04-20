package graphics.scenery.backends.vulkan

import glfw_.appBuffer
import graphics.scenery.Blending
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.utils.LazyLogger
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
import org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR
import org.lwjgl.vulkan.VK10.*
import org.slf4j.LoggerFactory
import vkn.*
import java.math.BigInteger
import java.nio.IntBuffer
import java.nio.LongBuffer

/**
 * VU - Vulkan Utils
 *
 * A collection of convenience methods for various Vulkan-related tasks
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */

fun Long.toHexString(): String {
    return String.format("0x%X", this)
}

fun BigInteger.toHexString(): String {
    return "0x${this.toString(16)}"
}

fun VkCommandBuffer.endCommandBuffer() {
    if (vkEndCommandBuffer(this) != VK_SUCCESS) {
        throw AssertionError("Failed to end command buffer $this")
    }
}

fun VkCommandBuffer.end(device: VulkanDevice, commandPool: VkCommandPool, queue: VkQueue?, flush: Boolean = true, dealloc: Boolean = false, submitInfoPNext: Pointer? = null) {
    if (adr == NULL)
        return

    end()

    if (flush && queue != null)
        submit(queue, submitInfoPNext)

    if (dealloc)
        device.vulkanDevice.freeCommandBuffer(commandPool, this)
}

fun VkCommandBuffer.submit(queue: VkQueue, submitInfoPNext: Pointer? = null, block: Boolean = true) {

    val submitInfo = vk.SubmitInfo {
        commandBuffers = appBuffer.pointerBufferOf(this@submit)
        next = submitInfoPNext?.adr ?: NULL
    }

    queue submit submitInfo
    queue.waitIdle()
}

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

fun Blending.BlendOp.toVulkan() = when (this) {
    Blending.BlendOp.add -> VK_BLEND_OP_ADD
    Blending.BlendOp.subtract -> VK_BLEND_OP_SUBTRACT
    Blending.BlendOp.min -> VK_BLEND_OP_MIN
    Blending.BlendOp.max -> VK_BLEND_OP_MAX
    Blending.BlendOp.reverse_subtract -> VK_BLEND_OP_REVERSE_SUBTRACT
}

object VU {

    private val logger by LazyLogger()

    inline fun run(name: String, function: () -> Int, allowedResults: List<Int> = emptyList()) {
        val result = function.invoke()

        if (result != VK_SUCCESS && result !in allowedResults) {
            LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
        }

        if (result in allowedResults) {
            LoggerFactory.getLogger("VulkanRenderer").debug("Call to $name did not result in error because return code(s) ${allowedResults.joinToString()} were explicitly tolerated.")
        }
    }

    inline fun run(name: String, function: () -> Int, cleanup: () -> Any) {
        val result = function.invoke()

        if (result != VK_SUCCESS) {
            LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
        }

        cleanup.invoke()
    }

    inline fun getInt(name: String, function: IntBuffer.() -> Int): Int {
        return stackPush().use { stack ->
            val receiver = stack.callocInt(2)
            val result = function.invoke(receiver)

            if (result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
            }

            val ret = receiver.get(0)

            ret
        }
    }

    inline fun getInts(name: String, count: Int, function: IntBuffer.() -> Int): IntBuffer {
        val receiver = memAllocInt(count)
        val result = function.invoke(receiver)

        if (result != VK_SUCCESS) {
            LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
        }

        return receiver
    }

    inline fun getLong(name: String, function: LongBuffer.() -> Int, cleanup: LongBuffer.() -> Any): Long {
        return stackPush().use { stack ->
            val receiver = stack.callocLong(1)
            val result = function.invoke(receiver)

            if (result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
                cleanup.invoke(receiver)
            }

            val ret = receiver.get(0)
            cleanup.invoke(receiver)

            ret
        }
    }

    inline fun getLongs(name: String, count: Int, function: LongBuffer.() -> Int, cleanup: LongBuffer.() -> Any): LongBuffer {
        val receiver = memAllocLong(count)
        val result = function.invoke(receiver)

        if (result != VK_SUCCESS) {
            LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
            cleanup.invoke(receiver)
        }

        cleanup.invoke(receiver)
        return receiver
    }

    inline fun getPointer(name: String, function: PointerBuffer.() -> Int, cleanup: PointerBuffer.() -> Any): Long {
        return stackPush().use { stack ->
            val receiver = stack.callocPointer(1)
            val result = function.invoke(receiver)

            if (result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
            }

            cleanup.invoke(receiver)

            receiver.get(0)
        }
    }

    inline fun getPointers(name: String, count: Int, function: PointerBuffer.() -> Int): PointerBuffer {
        val receiver = memAllocPointer(count)
        val result = function.invoke(receiver)

        if (result != VK_SUCCESS) {
            LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
        }

        return receiver
    }

    inline fun getPointers(name: String, count: Int, function: PointerBuffer.() -> Int, cleanup: PointerBuffer.() -> Any): PointerBuffer {
        val receiver = memAllocPointer(count)
        val result = function.invoke(receiver)

        if (result != VK_SUCCESS) {
            LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
            cleanup.invoke(receiver)
        }

        cleanup.invoke(receiver)

        return receiver
    }

    /**
     * Translates a Vulkan `VkResult` value to a String describing the result.

     * @param result the `VkResult` value
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

    fun VkCommandBuffer.setImageLayout(image: VkImage, oldImageLayout: VkImageLayout, newImageLayout: VkImageLayout, range: VkImageSubresourceRange) {

        val imageMemoryBarrier = vk.ImageMemoryBarrier {
            oldLayout = oldImageLayout
            newLayout = newImageLayout
            this.image = image
            subresourceRange = range
            srcAccessMask = oldImageLayout.accessMask

            when (newImageLayout) {
                VkImageLayout.TRANSFER_DST_OPTIMAL -> dstAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                VkImageLayout.TRANSFER_SRC_OPTIMAL -> {
                    srcAccessMask = srcAccessMask or VkAccess.TRANSFER_READ_BIT
                    dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                }
                VkImageLayout.COLOR_ATTACHMENT_OPTIMAL -> {
                    srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    dstAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                }
                VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> dstAccessMask = dstAccessMask or VkAccess.DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                VkImageLayout.SHADER_READ_ONLY_OPTIMAL -> {
                    if (srcAccessMask == 0)
                        dstAccessMask = VkAccess.HOST_WRITE_BIT or VkAccess.TRANSFER_WRITE_BIT
                    dstAccessMask = VkAccess.SHADER_READ_BIT.i
                }
                VkImageLayout.PRESENT_SRC_KHR -> dstAccessMask = dstAccessMask or VkAccess.MEMORY_READ_BIT
            }
        }

        val srcStageFlags = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
        val dstStageFlags = VkPipelineStage.FRAGMENT_SHADER_BIT.i

        pipelineBarrier(
            srcStageFlags, dstStageFlags,
            0, null, null,
            imageMemoryBarrier)
    }

    fun VkCommandBuffer.setImageLayout(image: VkImage, aspectMask: Int, oldImageLayout: VkImageLayout, newImageLayout: VkImageLayout) {

        val range = vk.ImageSubresourceRange {
            this.aspectMask = aspectMask
            baseMipLevel = 0
            levelCount = 1
            layerCount = 1
        }

        setImageLayout(image, oldImageLayout, newImageLayout, range)
    }

    fun VkDevice.newCommandBuffer(commandPool: VkCommandPool, level: VkCommandBufferLevel = VkCommandBufferLevel.PRIMARY, autostart: Boolean = false): VkCommandBuffer {
        val cmdBuf = getCommandBuffer(commandPool, level)

        if (autostart)
            cmdBuf.begin()

        return cmdBuf
    }

    fun newCommandBuffer(device: VulkanDevice, commandPool: VkCommandPool, level: Int = VK_COMMAND_BUFFER_LEVEL_PRIMARY, autostart: Boolean = false): VkCommandBuffer {
        val cmdBuf = device.vulkanDevice.getCommandBuffer(commandPool, VkCommandBufferLevel of level)

        if (autostart)
            cmdBuf.begin()

        return cmdBuf
    }

    fun beginCommandBuffer(commandBuffer: VkCommandBuffer, flags: Int = VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT) {
        stackPush().use { stack ->
            val cmdBufInfo = VkCommandBufferBeginInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .pNext(NULL)
                .flags(flags)

            VU.run("Beginning command buffer", { vkBeginCommandBuffer(commandBuffer, cmdBufInfo) }, {})
        }
    }

    fun createDescriptorSetLayout(
        device: VulkanDevice,
        type: VkDescriptorType = VkDescriptorType.UNIFORM_BUFFER_DYNAMIC,
        binding: Int = 0,
        descriptorNum: Int = 1,
        descriptorCount: Int = 1,
        shaderStages: VkShaderStageFlags = VkShaderStage.ALL.i): VkDescriptorSetLayout {

        val layoutBinding = vk.DescriptorSetLayoutBinding(descriptorNum)
        (binding until descriptorNum).forEach { i ->
            layoutBinding[i].apply {
                this.binding = i
                descriptorType = type
                this.descriptorCount = descriptorCount
                stageFlags = shaderStages
                immutableSamplers = null
            }
        }

        return device.vulkanDevice.createDescriptorSetLayout(cVkDescriptorSetLayoutCreateInfo { bindings = layoutBinding }).also {
            logger.debug("Created DSL ${it.toHexString()} with $descriptorNum descriptors with $descriptorCount elements.")
        }
    }

    fun VkDevice.createDescriptorSetLayout(types: List<Pair<VkDescriptorType, Int>>, binding: Int = 0, shaderStages: VkShaderStageFlags): VkDescriptorSetLayout {

        val layoutBinding = vk.DescriptorSetLayoutBinding(types.size)

        types.forEachIndexed { i, (type, count) ->
            layoutBinding[i].apply {
                this.binding = i + binding
                descriptorType = type
                descriptorCount = count
                stageFlags = shaderStages
                immutableSamplers = null
            }
        }

        return createDescriptorSetLayout(vk.DescriptorSetLayoutCreateInfo { bindings = layoutBinding }).apply {
            logger.debug("Created DSL ${toHexString()} with ${types.size} descriptors.")
        }
    }

    fun VkDevice.createDescriptorSetDynamic(descriptorPool: VkDescriptorPool, descriptorSetLayout: VkDescriptorSetLayout,
                                            bindingCount: Int, buffer: VulkanBuffer): VkDescriptorSet {

        logger.debug("Creating dynamic descriptor set with $bindingCount bindings, DSL=${descriptorSetLayout.toHexString()}")

        val allocInfo = vk.DescriptorSetAllocateInfo {
            this.descriptorPool = descriptorPool
            this.setLayouts = appBuffer.longBufferOf(descriptorSetLayout)
        }

        val descriptorSet = allocateDescriptorSets(allocInfo)

        val info = vk.DescriptorBufferInfo(1) {
            this.buffer = buffer.vulkanBuffer
            this.range = 2048
            this.offset = 0L
        }

        val writeDescriptorSet = vk.WriteDescriptorSet(bindingCount).also {
            it.forEachIndexed { i, set ->
                set.apply {
                    dstSet = descriptorSet
                    dstBinding = i
                    dstArrayElement = 0
                    bufferInfo = info
                    descriptorType = VkDescriptorType.UNIFORM_BUFFER_DYNAMIC
                }
            }
        }

        vkUpdateDescriptorSets(this, writeDescriptorSet, null)

        return descriptorSet
    }

    fun createDescriptorSet(device: VulkanDevice, descriptorPool: Long, descriptorSetLayout: Long, bindingCount: Int,
                            ubo: VulkanUBO.UBODescriptor, type: Int = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER): Long {
        logger.debug("Creating descriptor set with ${bindingCount} bindings, DSL=$descriptorSetLayout")
        return stackPush().use { stack ->
            val pDescriptorSetLayout = stack.callocLong(1).put(0, descriptorSetLayout)

            val allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .pNext(NULL)
                .descriptorPool(descriptorPool)
                .pSetLayouts(pDescriptorSetLayout)

            val descriptorSet = getLong("createDescriptorSet",
                { vkAllocateDescriptorSets(device.vulkanDevice, allocInfo, this) }, {})

            val d =
                VkDescriptorBufferInfo.callocStack(1, stack)
                    .buffer(ubo.buffer)
                    .range(ubo.range)
                    .offset(ubo.offset)

            val writeDescriptorSet = VkWriteDescriptorSet.callocStack(bindingCount, stack)

            (0 until bindingCount).forEach { i ->
                writeDescriptorSet[i]
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .pNext(NULL)
                    .dstSet(descriptorSet)
                    .dstBinding(i)
                    .pBufferInfo(d as VkDescriptorBufferInfo.Buffer)
                    .descriptorType(type)
            }

            vkUpdateDescriptorSets(device.vulkanDevice, writeDescriptorSet, null)

            descriptorSet
        }
    }

    fun createRenderTargetDescriptorSet(device: VulkanDevice, descriptorPool: Long, descriptorSetLayout: Long,
                                        rt: Map<String, RenderConfigReader.AttachmentConfig>,
                                        target: VulkanFramebuffer, onlyFor: String? = null): Long {

        return stackPush().use { stack ->
            val pDescriptorSetLayout = stack.callocLong(1).put(0, descriptorSetLayout)

            val allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .pNext(NULL)
                .descriptorPool(descriptorPool)
                .pSetLayouts(pDescriptorSetLayout)

            val descriptorSet = getLong("createDescriptorSet",
                { vkAllocateDescriptorSets(device.vulkanDevice, allocInfo, this) }, {})

            val descriptorWrites = if (onlyFor == null) {
                val writeDescriptorSet = VkWriteDescriptorSet.callocStack(rt.size, stack)

                rt.entries.forEachIndexed { i, entry ->
                    val attachment = target.attachments[entry.key]!!
                    val d = VkDescriptorImageInfo.callocStack(1, stack)

                    d
                        .imageView(attachment.imageView)
                        .sampler(target.framebufferSampler)
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
                        .imageView(attachment.imageView)
                        .sampler(target.framebufferSampler)
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
