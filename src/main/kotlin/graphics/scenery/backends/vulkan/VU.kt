package graphics.scenery.backends.vulkan

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.NativeResource
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT
import org.lwjgl.vulkan.KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR
import org.lwjgl.vulkan.KHRSurface.VK_ERROR_NATIVE_WINDOW_IN_USE_KHR
import org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.LoggerFactory
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.utils.LazyLogger
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.*

/**
 * VU - Vulkan Utils
 *
 * A conllection of convenience methods for various Vulkan-related tasks
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */

fun VkCommandBuffer.endCommandBuffer() {
    if(vkEndCommandBuffer(this) != VK_SUCCESS) {
        throw AssertionError("Failed to end command buffer $this")
    }
}

fun VkCommandBuffer.endCommandBuffer(device: VkDevice, commandPool: Long, queue: VkQueue?, flush: Boolean = true, dealloc: Boolean = false) {
    if (this.address() == NULL) {
        return
    }

    if(vkEndCommandBuffer(this) != VK_SUCCESS) {
        throw AssertionError("Failed to end command buffer $this")
    }

    if(flush && queue != null) {
        val submitInfo = VkSubmitInfo.calloc(1)

        VU.run(MemoryUtil.memAllocPointer(1).put(this).flip(), "endCommandBuffer", {
            submitInfo
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(this)

            vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE)
            vkQueueWaitIdle(queue)
        }, { submitInfo.free() })
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

                properties.free()
                return found.to(i)
            }
        }

        bits = bits shr 1
    }

    System.err.println("Memory type $memoryFlags not found for device")

    properties.free()

    return false.to(0)
}

class VU {

    companion object VU {
        private val logger by LazyLogger()

        inline fun <T: LongBuffer> run(receiver: T, name: String, function: T.() -> Int): Long {
            val result = function.invoke(receiver)

            if(result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
            }

            val ret = receiver.get(0)
            MemoryUtil.memFree(receiver)

            return ret
        }

        inline fun <T: LongBuffer> run(receiver: T, name: String, function: T.() -> Int, cleanup: T.() -> Any, free: Boolean = true): Long {
            val result = function.invoke(receiver)

            if(result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
                cleanup.invoke(receiver)
            }

            val ret = receiver.get(0)
            if(free) {
                MemoryUtil.memFree(receiver)
            }

            cleanup.invoke(receiver)

            return ret
        }

        inline fun <T: PointerBuffer> run(receiver: T, name: String, function: T.() -> Int, cleanup: T.() -> Any, free: Boolean = true): Long {
            val result = function.invoke(receiver)

            if(result != VK_SUCCESS) {
                LoggerFactory.getLogger("VulkanRenderer").error("Call to $name failed: ${translate(result)}")
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

            val commandBuffer = run(MemoryUtil.memAllocPointer(1),"Creating command buffer",
                { vkAllocateCommandBuffers(device, cmdBufAllocateInfo, this) },
                { cmdBufAllocateInfo.free() })

            return VkCommandBuffer(commandBuffer, device)
        }

        fun newCommandBuffer(device: VkDevice, commandPool: Long, level: Int = VK_COMMAND_BUFFER_LEVEL_PRIMARY, autostart: Boolean = false): VkCommandBuffer {
            val cmdBuf = newCommandBuffer(device, commandPool, level)

            if(autostart) {
               beginCommandBuffer(cmdBuf)
            }

            return cmdBuf
        }

        fun beginCommandBuffer(commandBuffer: VkCommandBuffer, flags: Int = VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT) {
            val cmdBufInfo = VkCommandBufferBeginInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .pNext(NULL)
                .flags(flags)

            vkBeginCommandBuffer(commandBuffer, cmdBufInfo)

            cmdBufInfo.free()
        }

        fun getMemoryType(deviceMemoryProperties: VkPhysicalDeviceMemoryProperties, typeBits: Int, properties: Int, typeIndex: IntBuffer): Boolean {
            var bits = typeBits
            for (i in 0..31) {
                if (bits and 1 == 1) {
                    if (deviceMemoryProperties.memoryTypes(i).propertyFlags() and properties == properties) {
                        typeIndex.put(0, i)
                        return true
                    }
                }
                bits = bits shr 1
            }
            return false
        }

        fun createDescriptorSetLayout(device: VkDevice, type: Int = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, binding: Int = 0, descriptorNum: Int = 1, descriptorCount: Int = 1, shaderStages: Int = VK_SHADER_STAGE_ALL_GRAPHICS): Long {
            val layoutBinding = VkDescriptorSetLayoutBinding.calloc(descriptorNum)
            (binding..descriptorNum - 1).forEach { i ->
                layoutBinding[i]
                    .binding(i)
                    .descriptorType(type)
                    .descriptorCount(descriptorCount)
                    .stageFlags(shaderStages)
                    .pImmutableSamplers(null)
            }

            val descriptorLayout = VkDescriptorSetLayoutCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pNext(NULL)
                .pBindings(layoutBinding)

            val descriptorSetLayout = run(MemoryUtil.memAllocLong(1), "vkCreateDescriptorSetLayout",
                function = { vkCreateDescriptorSetLayout(device, descriptorLayout, null, this) },
                cleanup = { descriptorLayout.free(); layoutBinding.free() }
            )

            logger.debug("Created DSL $descriptorSetLayout with $descriptorNum descriptors with $descriptorCount elements.")

            return descriptorSetLayout
        }

        fun createDescriptorSetLayout(device: VkDevice, types: List<Pair<Int, Int>>, binding: Int = 0, shaderStages: Int): Long {
            val layoutBinding = VkDescriptorSetLayoutBinding.calloc(types.size)

            types.forEachIndexed { i, (type, count) ->
                layoutBinding[i]
                    .binding(i + binding)
                    .descriptorType(type)
                    .descriptorCount(count)
                    .stageFlags(shaderStages)
                    .pImmutableSamplers(null)
            }

            val descriptorLayout = VkDescriptorSetLayoutCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pNext(NULL)
                .pBindings(layoutBinding)

            val descriptorSetLayout = run(MemoryUtil.memAllocLong(1), "vkCreateDescriptorSetLayout",
                function = { vkCreateDescriptorSetLayout(device, descriptorLayout, null, this) },
                cleanup = { descriptorLayout.free(); layoutBinding.free() }
            )

            logger.debug("Created DSL $descriptorSetLayout with ${types.size} descriptors.")

            return descriptorSetLayout
        }

        fun createDescriptorSetDynamic(device: VkDevice, descriptorPool: Long, descriptorSetLayout: Long,
                                       bindingCount: Int, buffer: VulkanBuffer): Long {
            logger.debug("Creating dynamic descriptor set with ${bindingCount} bindings, DSL=$descriptorSetLayout")

            val pDescriptorSetLayout = MemoryUtil.memAllocLong(1)
            pDescriptorSetLayout.put(0, descriptorSetLayout)

            val allocInfo = VkDescriptorSetAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .pNext(NULL)
                .descriptorPool(descriptorPool)
                .pSetLayouts(pDescriptorSetLayout)

            val descriptorSet = run(MemoryUtil.memAllocLong(1), "createDescriptorSet",
                { vkAllocateDescriptorSets(device, allocInfo, this) },
                { allocInfo.free(); MemoryUtil.memFree(pDescriptorSetLayout) })

            val d = VkDescriptorBufferInfo.calloc(1)
                    .buffer(buffer.buffer)
                    .range(2048)
                    .offset(0L)

            val writeDescriptorSet = VkWriteDescriptorSet.calloc(bindingCount)

            (0..bindingCount-1).forEach { i ->
                writeDescriptorSet[i]
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .pNext(NULL)
                    .dstSet(descriptorSet)
                    .dstBinding(i)
                    .dstArrayElement(0)
                    .pBufferInfo(d)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
            }

            vkUpdateDescriptorSets(device, writeDescriptorSet, null)
            writeDescriptorSet.free()
            (d as NativeResource).free()

            return descriptorSet
        }

        fun createDescriptorSet(device: VkDevice, descriptorPool: Long, descriptorSetLayout: Long, bindingCount: Int,
                                ubo: VulkanUBO.UBODescriptor, type: Int = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER): Long {
            logger.debug("Creating descriptor set with ${bindingCount} bindings, DSL=$descriptorSetLayout")

            val pDescriptorSetLayout = MemoryUtil.memAllocLong(1)
            pDescriptorSetLayout.put(0, descriptorSetLayout)

            val allocInfo = VkDescriptorSetAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .pNext(NULL)
                .descriptorPool(descriptorPool)
                .pSetLayouts(pDescriptorSetLayout)

            val descriptorSet = run(MemoryUtil.memAllocLong(1), "createDescriptorSet",
                { vkAllocateDescriptorSets(device, allocInfo, this) },
                { allocInfo.free(); MemoryUtil.memFree(pDescriptorSetLayout) })

            val d =
                VkDescriptorBufferInfo.calloc(1)
                    .buffer(ubo.buffer)
                    .range(ubo.range)
                    .offset(ubo.offset)

            val writeDescriptorSet = VkWriteDescriptorSet.calloc(bindingCount)

            (0..bindingCount-1).forEach { i ->
                writeDescriptorSet[i]
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .pNext(NULL)
                    .dstSet(descriptorSet)
                    .dstBinding(i)
                    .pBufferInfo(d as VkDescriptorBufferInfo.Buffer)
                    .descriptorType(type)
            }

            vkUpdateDescriptorSets(device, writeDescriptorSet, null)
            writeDescriptorSet.free()
            (d as NativeResource).free()

            return descriptorSet
        }

        fun createRenderTargetDescriptorSet(device: VkDevice, descriptorPool: Long, descriptorSetLayout: Long,
                                             rt: Map<String, RenderConfigReader.AttachmentConfig>,
                                             target: VulkanFramebuffer): Long {

            val pDescriptorSetLayout = MemoryUtil.memAllocLong(1)
            pDescriptorSetLayout.put(0, descriptorSetLayout)

            val allocInfo = VkDescriptorSetAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .pNext(NULL)
                .descriptorPool(descriptorPool)
                .pSetLayouts(pDescriptorSetLayout)

            val descriptorSet = run(MemoryUtil.memAllocLong(1), "createDescriptorSet",
                { vkAllocateDescriptorSets(device, allocInfo, this) },
                { allocInfo.free(); MemoryUtil.memFree(pDescriptorSetLayout) })

            val writeDescriptorSet = VkWriteDescriptorSet.calloc(rt.size)
            val dlist = ArrayList<VkDescriptorImageInfo.Buffer>()

            rt.entries.forEachIndexed { i, entry ->
                val attachment = target.attachments[entry.key]!!
                val d = VkDescriptorImageInfo.calloc(1)
                dlist.add(d)

                d
                    .imageView(attachment.imageView.get(0))
                    .sampler(target.framebufferSampler.get(0))
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

            vkUpdateDescriptorSets(device, writeDescriptorSet, null)
            writeDescriptorSet.free()
            dlist.forEach { it.free() }

            logger.debug("Creating framebuffer attachment descriptor $descriptorSet set with ${rt.size} bindings, DSL=$descriptorSetLayout")
            return descriptorSet
        }

        fun createBuffer(device: VkDevice, deviceMemoryProperties: VkPhysicalDeviceMemoryProperties, usage: Int, memoryProperties: Int, wantAligned: Boolean = false, allocationSize: Long = 0): VulkanBuffer {
            val memory = MemoryUtil.memAllocLong(1)
            val memTypeIndex = MemoryUtil.memAllocInt(1)

            val reqs = VkMemoryRequirements.calloc()
            val bufferInfo = VkBufferCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .pNext(NULL)
                .usage(usage)
                .size(allocationSize)

            val buffer = run(MemoryUtil.memAllocLong(1), "Creating buffer",
                { vkCreateBuffer(device, bufferInfo, null, this) })
            vkGetBufferMemoryRequirements(device, buffer, reqs)

            bufferInfo.free()

            val allocInfo = VkMemoryAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .pNext(NULL)

            getMemoryType(deviceMemoryProperties,
                reqs.memoryTypeBits(),
                memoryProperties,
                memTypeIndex)

            val size = if (wantAligned) {
                if (reqs.size().rem(reqs.alignment()) == 0L) {
                    reqs.size()
                } else {
                    reqs.size() + reqs.alignment() - (reqs.size().rem(reqs.alignment()))
                }
            } else {
                reqs.size()
            }

            allocInfo.allocationSize(size)
                .memoryTypeIndex(memTypeIndex.get(0))

            vkAllocateMemory(device, allocInfo, null, memory)
            vkBindBufferMemory(device, buffer, memory.get(0), 0)

            val vb = VulkanBuffer(device, memory = memory.get(0), buffer = buffer, size = size)
            vb.alignment = reqs.alignment()

            reqs.free()
            allocInfo.free()
            MemoryUtil.memFree(memTypeIndex)
            MemoryUtil.memFree(memory)

            return vb
        }
    }


}
