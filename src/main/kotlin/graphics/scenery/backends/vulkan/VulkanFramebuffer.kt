package graphics.scenery.backends.vulkan

import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memAllocLong
import org.lwjgl.system.Struct
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import vkn.*
import java.nio.LongBuffer
import java.util.*

/**
 *

 * @author Ulrik Günther @ulrik.is>
 */
open class VulkanFramebuffer(protected val device: VulkanDevice,
                             protected var commandPool: VkCommandPool,
                             var width: Int,
                             var height: Int,
                             val commandBuffer: VkCommandBuffer,
                             var shouldClear: Boolean = true, val sRGB: Boolean = false) : AutoCloseable {
    protected val logger by LazyLogger()

    var framebuffer: VkFramebuffer = NULL
    var renderPass: VkRenderPass = NULL
    var framebufferSampler: VkSampler = NULL
    var outputDescriptorSet: VkDescriptorSet = -1L

    protected var initialized: Boolean = false

    enum class VkFramebufferAttachment { COLOR, DEPTH }

    inner class VulkanFramebufferAttachment : AutoCloseable {
        var image: VkImage = NULL
        var memory: VkDeviceMemory = NULL
        var imageView: VkImageView = NULL
        var format = VkFormat.UNDEFINED

        var type: VkFramebufferAttachment = VkFramebufferAttachment.COLOR
        var desc: VkAttachmentDescription = VkAttachmentDescription.calloc()

        var fromSwapchain = false

        override fun close() {
            with(device.vulkanDevice) {
                destroyImageView(imageView)
                if (image != NULL && !fromSwapchain)
                    destroyImage(image)
                if (memory != NULL)
                    freeMemory(memory)
            }
            desc.free()
        }
    }

    var attachments = LinkedHashMap<String, VulkanFramebufferAttachment>()

    protected fun createAttachment(format: VkFormat, usage: VkImageUsage): VulkanFramebufferAttachment {
        val att = VulkanFramebufferAttachment()
        var aspectMask: VkImageAspectFlags = 0
        var imageLayout = VkImageLayout.UNDEFINED

        att.format = format

        if (usage == VkImageUsage.COLOR_ATTACHMENT_BIT) {
            aspectMask = VkImageAspect.COLOR_BIT.i
            imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
        }

        if (usage == VkImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT) {
            aspectMask = VkImageAspect.DEPTH_BIT.i
            imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
        }

        val imageExtent = VkExtent3D.calloc()
            .width(width)
            .height(height)
            .depth(1)

        val image = VkImageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .pNext(NULL)
            .imageType(VK_IMAGE_TYPE_2D)
            .format(att.format.i)
            .extent(imageExtent)
            .mipLevels(1)
            .arrayLayers(1)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .tiling(VK_IMAGE_TILING_OPTIMAL)
            .usage(usage or VkImageUsage.SAMPLED_BIT or VkImageUsage.TRANSFER_SRC_BIT or VkImageUsage.TRANSFER_DST_BIT)


        att.image = device.vulkanDevice createImage image

        val requirements = VkMemoryRequirements.calloc()
        vkGetImageMemoryRequirements(device.vulkanDevice, att.image, requirements)

        val allocation = VkMemoryAllocateInfo.calloc()
            .allocationSize(requirements.size())
            .memoryTypeIndex(device.getMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)[0])
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)

        att.memory = device.vulkanDevice allocateMemory allocation
        vkBindImageMemory(device.vulkanDevice, att.image, att.memory, 0)

        requirements.free()
        allocation.free()

        VU.setImageLayout(
            commandBuffer,
            att.image,
            aspectMask,
            VkImageLayout.UNDEFINED,
            if (usage == VkImageUsage.COLOR_ATTACHMENT_BIT) VkImageLayout.SHADER_READ_ONLY_OPTIMAL
            else imageLayout
        )

        val subresourceRange = VkImageSubresourceRange.calloc()
            .aspectMask(aspectMask)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1)

        val iv = VkImageViewCreateInfo.calloc()
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .format(format.i)
            .subresourceRange(subresourceRange)
            .image(att.image)
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .pNext(NULL)

        att.imageView = device.vulkanDevice createImageView iv
        iv.free()
        subresourceRange.free()

        return att
    }

    fun addFloatBuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format = when (channelDepth) {
            16 -> VkFormat.R16_SFLOAT
            32 -> VkFormat.R32_SFLOAT
            else -> {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit."); VkFormat.R16_SFLOAT
            }
        }

        val att = createAttachment(format, VkImageUsage.COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD to VK_ATTACHMENT_LOAD_OP_LOAD
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR to VK_ATTACHMENT_LOAD_OP_DONT_CARE
        }

        val initialImageLayout = if (!shouldClear) {
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        } else {
            VK_IMAGE_LAYOUT_UNDEFINED
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(initialImageLayout)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format.i)

        attachments.put(name, att)

        return this
    }

    fun addFloatRGBuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format = when (channelDepth) {
            16 -> VkFormat.R16G16_SFLOAT
            32 -> VkFormat.R32G32_SFLOAT
            else -> {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit."); VkFormat.R16G16_SFLOAT
            }
        }

        val att = createAttachment(format, VkImageUsage.COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD to VK_ATTACHMENT_LOAD_OP_LOAD
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR to VK_ATTACHMENT_LOAD_OP_DONT_CARE
        }

        val initialImageLayout = if (!shouldClear) {
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        } else {
            VK_IMAGE_LAYOUT_UNDEFINED
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(initialImageLayout)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format.i)

        attachments.put(name, att)

        return this
    }

    fun addFloatRGBBuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format = when (channelDepth) {
            16 -> VkFormat.R16G16B16_SFLOAT
            32 -> VkFormat.R32G32B32_SFLOAT
            else -> {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit."); VkFormat.R16G16B16A16_SFLOAT
            }
        }

        val att = createAttachment(format, VkImageUsage.COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD to VK_ATTACHMENT_LOAD_OP_LOAD
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR to VK_ATTACHMENT_LOAD_OP_DONT_CARE
        }

        val initialImageLayout = if (!shouldClear) {
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        } else {
            VK_IMAGE_LAYOUT_UNDEFINED
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(initialImageLayout)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format.i)

        attachments.put(name, att)

        return this
    }

    fun addFloatRGBABuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format = when (channelDepth) {
            16 -> VkFormat.R16G16B16A16_SFLOAT
            32 -> VkFormat.R32G32B32A32_SFLOAT
            else -> {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit."); VkFormat.R16G16B16A16_SFLOAT
            }
        }

        val att = createAttachment(format, VkImageUsage.COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD to VK_ATTACHMENT_LOAD_OP_LOAD
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR to VK_ATTACHMENT_LOAD_OP_DONT_CARE
        }

        val initialImageLayout = if (!shouldClear) {
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        } else {
            VK_IMAGE_LAYOUT_UNDEFINED
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(initialImageLayout)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format.i)

        attachments.put(name, att)

        return this
    }

    fun addUnsignedByteRGBABuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format = when (channelDepth) {
            8 -> if (sRGB) {
                VkFormat.R8G8B8A8_SRGB
            } else {
                VkFormat.R8G8B8A8_UNORM
            }
            16 -> VkFormat.R16G16B16A16_UNORM
            else -> {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit."); VkFormat.R16G16B16A16_UINT
            }
        }

        val att = createAttachment(format, VkImageUsage.COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD to VK_ATTACHMENT_LOAD_OP_LOAD
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR to VK_ATTACHMENT_LOAD_OP_DONT_CARE
        }

        val initialImageLayout = if (!shouldClear) {
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        } else {
            VK_IMAGE_LAYOUT_UNDEFINED
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(initialImageLayout)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format.i)

        attachments.put(name, att)

        return this
    }

    fun addUnsignedByteRBuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format = when (channelDepth) {
            8 -> VkFormat.R8_UNORM
            16 -> VkFormat.R16_UNORM
            else -> {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit."); VkFormat.R16_UNORM
            }
        }

        val att = createAttachment(format, VkImageUsage.COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD to VK_ATTACHMENT_LOAD_OP_LOAD
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR to VK_ATTACHMENT_LOAD_OP_DONT_CARE
        }

        val initialImageLayout = if (!shouldClear) {
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        } else {
            VK_IMAGE_LAYOUT_UNDEFINED
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(initialImageLayout)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format.i)

        attachments.put(name, att)

        return this
    }

    fun addDepthBuffer(name: String, depth: Int): VulkanFramebuffer {
        val format = when (depth) {
            16 -> VkFormat.D16_UNORM
            24 -> VkFormat.D24_UNORM_S8_UINT
            32 -> VkFormat.D32_SFLOAT
            else -> {
                logger.warn("Unsupported channel depth $depth, using 32 bit."); VkFormat.D32_SFLOAT
            }
        }

        val bestSupportedFormat = getBestDepthFormat(format).first()

        val att = createAttachment(bestSupportedFormat, VkImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_DONT_CARE to VK_ATTACHMENT_LOAD_OP_LOAD
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR to VK_ATTACHMENT_LOAD_OP_CLEAR
        }

        val initialImageLayout = VK_IMAGE_LAYOUT_UNDEFINED

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_STORE)
            .initialLayout(initialImageLayout)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(bestSupportedFormat.i)

        att.type = VkFramebufferAttachment.DEPTH

        attachments.put(name, att)

        return this
    }

    fun addSwapchainAttachment(name: String, swapchain: Swapchain, index: Int): VulkanFramebuffer {
        val att = VulkanFramebufferAttachment()

        att.image = swapchain.images!![index]
        att.imageView = swapchain.imageViews!![index]
        att.type = VkFramebufferAttachment.COLOR
        att.fromSwapchain = true

        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD to VK_ATTACHMENT_LOAD_OP_LOAD
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR to VK_ATTACHMENT_LOAD_OP_DONT_CARE
        }

        val initialImageLayout = VK_IMAGE_LAYOUT_UNDEFINED

        att.desc
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(initialImageLayout)
            .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
            .format(if (sRGB) {
                VK_FORMAT_B8G8R8A8_SRGB
            } else {
                VK_FORMAT_B8G8R8A8_UNORM
            })

        attachments.put(name, att)

        return this
    }

    protected fun getAttachmentDescBuffer(): VkAttachmentDescription.Buffer {
        val descriptionBuffer = VkAttachmentDescription.calloc(attachments.size)
        attachments.values.forEach { descriptionBuffer.put(it.desc) }

        return descriptionBuffer.flip()
    }

    protected fun getAttachmentImageViews(): LongBuffer {
        val ivBuffer = memAllocLong(attachments.size)
        attachments.values.forEach { ivBuffer.put(it.imageView) }

        ivBuffer.flip()

        return ivBuffer
    }

    fun createRenderpassAndFramebuffer() {
        val colorDescs = VkAttachmentReference.calloc(attachments.filter { it.value.type == VkFramebufferAttachment.COLOR }.size)

        attachments.values.filter { it.type == VkFramebufferAttachment.COLOR }.forEachIndexed { i, _ ->
            colorDescs[i]
                .attachment(i)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
        }

        val depthDescs: VkAttachmentReference? = if (attachments.any { it.value.type == VkFramebufferAttachment.DEPTH }) {
            VkAttachmentReference.calloc()
                .attachment(colorDescs.limit())
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
        } else {
            null
        }


        logger.trace("Subpass for has ${colorDescs.remaining()} color attachments")

        val subpass = VkSubpassDescription.calloc(1)
            .pColorAttachments(colorDescs)
            .colorAttachmentCount(colorDescs.remaining())
            .pDepthStencilAttachment(depthDescs)
            .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
            .pInputAttachments(null)
            .pPreserveAttachments(null)
            .pResolveAttachments(null)
            .flags(0)

        val dependencyChain = VkSubpassDependency.calloc(2)

        dependencyChain[0]
            .srcSubpass(VK_SUBPASS_EXTERNAL)
            .dstSubpass(0)
            .srcStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
            .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
            .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)

        dependencyChain[1]
            .srcSubpass(0)
            .dstSubpass(VK_SUBPASS_EXTERNAL)
            .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
            .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
            .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)

        val attachmentDescs = getAttachmentDescBuffer()
        val renderPassInfo = VkRenderPassCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            .pAttachments(attachmentDescs)
            .pSubpasses(subpass)
            .pDependencies(dependencyChain)
            .pNext(NULL)

        renderPass = device.vulkanDevice createRenderPass renderPassInfo

        logger.trace("Created renderpass $renderPass")

        val attachmentImageViews = getAttachmentImageViews()
        val fbinfo = VkFramebufferCreateInfo.calloc()
            .default()
            .renderPass(renderPass)
            .pAttachments(attachmentImageViews)
            .width(width)
            .height(height)
            .layers(1)

        framebuffer = device createFramebuffer fbinfo

        val samplerCreateInfo = VkSamplerCreateInfo.calloc()
            .default()
            .magFilter(VK_FILTER_LINEAR)
            .minFilter(VK_FILTER_LINEAR)
            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
            .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
            .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
            .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
            .mipLodBias(0.0f)
            .maxAnisotropy(1.0f)
            .minLod(0.0f)
            .maxLod(1.0f)
            .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)

        framebufferSampler = device.vulkanDevice createSampler samplerCreateInfo

        renderPassInfo.free()
        samplerCreateInfo.free()
        subpass.free()
        colorDescs.free()
        depthDescs?.free()
        dependencyChain.free()

        initialized = true
    }

    fun <T : Struct> T.default(): T {
        if (this is VkSamplerCreateInfo) {
            this.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO).pNext(NULL)
        } else if (this is VkFramebufferCreateInfo) {
            this.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO).pNext(NULL)
        }

        return this
    }

    override fun toString(): String {
        return "VulkanFramebuffer"
    }

    val id: Int
        get() {
            if (initialized) {
                return 0
            } else {
                return -1
            }
        }

    private fun getBestDepthFormat(preferredFormat: VkFormat): List<VkFormat> {
        // this iterates through the list of possible (though not all required formats)
        // and returns the first one that is possible to use as a depth buffer on the
        // given physical device.
        val props = VkFormatProperties.calloc()
        val format = arrayOf(
            preferredFormat,
            VkFormat.D32_SFLOAT,
            VkFormat.D32_SFLOAT_S8_UINT,
            VkFormat.D24_UNORM_S8_UINT,
            VkFormat.D16_UNORM_S8_UINT,
            VkFormat.D16_UNORM
        ).filter {
            vkGetPhysicalDeviceFormatProperties(device.physicalDevice, it.i, props)

            props.optimalTilingFeatures() and VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT > 0
        }

        logger.debug("Using $format as depth format.")

        props.free()
        return format
    }

    fun colorAttachmentCount() = attachments.count { it.value.type == VkFramebufferAttachment.COLOR }

    fun depthAttachmentCount() = attachments.count { it.value.type == VkFramebufferAttachment.DEPTH }

    override fun close() {
        if (initialized) {
            attachments.values.forEach { it.close() }

            device.vulkanDevice.apply {
                destroyRenderPass(renderPass)
                destroySampler(framebufferSampler)
                destroyFramebuffer(framebuffer)
            }
            initialized = false
        }
    }
}

