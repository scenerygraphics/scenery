package graphics.scenery.backends.vulkan

import org.lwjgl.vulkan.*
import java.util.*
import org.lwjgl.vulkan.VK10.*
import java.nio.LongBuffer
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.Struct
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *

 * @author Ulrik Günther @ulrik.is>
 */
open class VulkanFramebuffer(protected var device: VkDevice,
                        protected var physicalDevice: VkPhysicalDevice,
                        protected var commandPool: Long,
                        var width: Int,
                        var height: Int,
                        var commandBuffer: VkCommandBuffer,
                        var shouldClear: Boolean = true): AutoCloseable {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderer")

    var framebuffer = memAllocLong(1)
    var renderPass = memAllocLong(1)
    var framebufferSampler = memAllocLong(1)
    var outputDescriptorSet: Long = -1L

    protected var initialized: Boolean = false

    enum class VulkanFramebufferType { COLOR_ATTACHMENT, DEPTH_ATTACHMENT }

    inner class VulkanFramebufferAttachment: AutoCloseable {
        var image: Long = -1L
        var memory: LongBuffer = memAllocLong(1)
        var imageView: LongBuffer = memAllocLong(1)
        var format: Int = 0

        var type: VulkanFramebufferType = VulkanFramebufferType.COLOR_ATTACHMENT
        var desc: VkAttachmentDescription = VkAttachmentDescription.calloc()

        var fromSwapchain = false

        init {
            memory.put(0, -1L)
        }

        override fun close() {
            vkDestroyImageView(device, imageView.get(0), null)
            memFree(imageView)

            if(image != -1L && fromSwapchain == false) {
                vkDestroyImage(device, image, null)
            }

            if(memory.get(0) != -1L) {
                vkFreeMemory(device, memory.get(0), null)
            }

            memFree(memory)

            desc.free()
        }
    }

    var attachments = LinkedHashMap<String, VulkanFramebufferAttachment>()

    protected fun createAttachment(format: Int, usage: Int): VulkanFramebufferAttachment {
        val a = VulkanFramebufferAttachment()
        var aspectMask: Int = 0
        var imageLayout: Int = 0

        a.format = format

        if (usage == VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) {
            aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
            imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
        }

        if (usage == VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) {
            aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT
            imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
        }

        val imageExtent = VkExtent3D.calloc()
            .width(width)
            .height(height)
            .depth(1)

        val image = VkImageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .pNext(NULL)
            .imageType(VK_IMAGE_TYPE_2D)
            .format(a.format)
            .extent(imageExtent)
            .mipLevels(1)
            .arrayLayers(1)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .tiling(VK_IMAGE_TILING_OPTIMAL)
            .usage(usage or VK_IMAGE_USAGE_SAMPLED_BIT or VK_IMAGE_USAGE_TRANSFER_SRC_BIT or VK_IMAGE_USAGE_TRANSFER_DST_BIT)


        val images = memAllocLong(1)
        vkCreateImage(device, image, null, images)
        a.image = images.get(0)

        memFree(images)
        image.free()
        imageExtent.free()

        val requirements = VkMemoryRequirements.calloc()
        vkGetImageMemoryRequirements(device, a.image, requirements)

        val allocation = VkMemoryAllocateInfo.calloc()
            .allocationSize(requirements.size())
            .memoryTypeIndex(physicalDevice.getMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT).second)
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)

        vkAllocateMemory(device, allocation, null, a.memory)
        vkBindImageMemory(device, a.image, a.memory.get(0), 0)

        requirements.free()
        allocation.free()

        VU.setImageLayout(
            commandBuffer,
            a.image,
            aspectMask,
            VK_IMAGE_LAYOUT_UNDEFINED,
            if (usage == VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
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
            .format(format)
            .subresourceRange(subresourceRange)
            .image(a.image)
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .pNext(NULL)

        vkCreateImageView(device, iv, null, a.imageView)
        iv.free()
        subresourceRange.free()

        return a
    }

    fun addFloatBuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format: Int = when(channelDepth) {
            16 -> VK_FORMAT_R16_SFLOAT
            32 -> VK_FORMAT_R32_SFLOAT
            else -> { System.err.println("Unsupported channel depth $channelDepth, using 16 bit."); VK_FORMAT_R16_SFLOAT }
        }

        val att = createAttachment(format, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if(!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD.to(VK_ATTACHMENT_LOAD_OP_LOAD)
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR.to(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format)

        attachments.put(name, att)

        return this
    }

    fun addFloatRGBuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format: Int = when(channelDepth) {
            16 -> VK_FORMAT_R16G16_SFLOAT
            32 -> VK_FORMAT_R32G32_SFLOAT
            else -> { System.err.println("Unsupported channel depth $channelDepth, using 16 bit."); VK_FORMAT_R16G16_SFLOAT }
        }

        val att = createAttachment(format, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if(!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD.to(VK_ATTACHMENT_LOAD_OP_LOAD)
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR.to(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format)

        attachments.put(name, att)

        return this
    }

    fun addFloatRGBBuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format: Int = when(channelDepth) {
            16 -> VK_FORMAT_R16G16B16_SFLOAT
            32 -> VK_FORMAT_R32G32B32_SFLOAT
            else -> { System.err.println("Unsupported channel depth $channelDepth, using 16 bit."); VK_FORMAT_R16G16B16A16_SFLOAT }
        }

        val att = createAttachment(format, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if(!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD.to(VK_ATTACHMENT_LOAD_OP_LOAD)
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR.to(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format)

        attachments.put(name, att)

        return this
    }

    fun addFloatRGBABuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format: Int = when(channelDepth) {
            16 -> VK_FORMAT_R16G16B16A16_SFLOAT
            32 -> VK_FORMAT_R32G32B32A32_SFLOAT
            else -> { System.err.println("Unsupported channel depth $channelDepth, using 16 bit."); VK_FORMAT_R16G16B16A16_SFLOAT }
        }

        val att = createAttachment(format, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if(!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD.to(VK_ATTACHMENT_LOAD_OP_LOAD)
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR.to(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format)

        attachments.put(name, att)

        return this
    }

    fun addUnsignedByteRGBABuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format: Int = when(channelDepth) {
            8 -> VK_FORMAT_R8G8B8A8_SRGB
            16 -> VK_FORMAT_R16G16B16A16_UNORM
            else -> { System.err.println("Unsupported channel depth $channelDepth, using 16 bit."); VK_FORMAT_R16G16B16A16_UINT }
        }

        val att = createAttachment(format, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if(!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD.to(VK_ATTACHMENT_LOAD_OP_LOAD)
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR.to(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format)

        attachments.put(name, att)

        return this
    }

    fun addDepthBuffer(name: String, depth: Int): VulkanFramebuffer {
        val format: Int = when(depth) {
            16 -> VK_FORMAT_D16_UNORM
            24 -> VK_FORMAT_D24_UNORM_S8_UINT
            32 -> VK_FORMAT_D32_SFLOAT
            else -> { System.err.println("Unsupported channel depth $depth, using 32 bit."); VK_FORMAT_D32_SFLOAT }
        }

        val bestSupportedFormat = getBestDepthFormat(format).first()

        val att = createAttachment(bestSupportedFormat, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = if(!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD.to(VK_ATTACHMENT_LOAD_OP_LOAD)
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR.to(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
        }

        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_STORE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(bestSupportedFormat)

        att.type = VulkanFramebufferType.DEPTH_ATTACHMENT

        attachments.put(name, att)

        return this
    }

    fun addSwapchainAttachment(name: String, swapchain: Swapchain, index: Int): VulkanFramebuffer {
        val att = VulkanFramebufferAttachment()

        att.image = swapchain.images!!.get(index)
        att.imageView.put(swapchain.imageViews!!.get(index))
        att.type = VulkanFramebufferType.COLOR_ATTACHMENT
        att.fromSwapchain = true

        val (loadOp, stencilLoadOp) = if(!shouldClear) {
            VK_ATTACHMENT_LOAD_OP_LOAD.to(VK_ATTACHMENT_LOAD_OP_LOAD)
        } else {
            VK_ATTACHMENT_LOAD_OP_CLEAR.to(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
        }

        att.desc
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(loadOp)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(stencilLoadOp)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
            .format(VK_FORMAT_B8G8R8A8_SRGB)

        attachments.put(name, att)

        return this
    }

    protected fun getAttachmentDescBuffer(): VkAttachmentDescription.Buffer {
        val descriptionBuffer = VkAttachmentDescription.calloc(attachments.size)
        attachments.values.forEach{ descriptionBuffer.put(it.desc) }

        return descriptionBuffer.flip()
    }

    protected fun getAttachmentImageViews(): LongBuffer {
        val ivBuffer = memAllocLong(attachments.size)
        attachments.values.forEach{ ivBuffer.put(it.imageView.get(0)) }

        ivBuffer.flip()

        return ivBuffer
    }

    fun createRenderpassAndFramebuffer() {
        val colorDescs = VkAttachmentReference.calloc(attachments.filter { it.value.type == VulkanFramebufferType.COLOR_ATTACHMENT }.size)

        attachments.values.filter { it.type == VulkanFramebufferType.COLOR_ATTACHMENT }.forEachIndexed { i, _ ->
            colorDescs[i]
                .attachment(i)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
        }

        val depthDescs = if(attachments.filter { it.value.type == VulkanFramebufferType.DEPTH_ATTACHMENT }.isNotEmpty()) {
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

        renderPass.put(0, VU.run(memAllocLong(1), "create renderpass",
            { vkCreateRenderPass(device, renderPassInfo, null, this) },
            { attachmentDescs.free() }))

        logger.trace("Created renderpass ${renderPass.get(0)}")

        val attachmentImageViews = getAttachmentImageViews()
        val fbinfo = VkFramebufferCreateInfo.calloc()
            .default()
            .renderPass(renderPass.get(0))
            .pAttachments(attachmentImageViews)
            .width(width)
            .height(height)
            .layers(1)

        framebuffer.put(0, VU.run(memAllocLong(1), "create framebuffer",
            { vkCreateFramebuffer(device, fbinfo, null, this) },
            { fbinfo.free(); memFree(attachmentImageViews); }))

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

        vkCreateSampler(device, samplerCreateInfo, null, this.framebufferSampler)

        renderPassInfo.free()
        samplerCreateInfo.free()
        subpass.free()
        colorDescs.free()
        depthDescs?.free()
        dependencyChain.free()

        initialized = true
    }

    inline fun <T: Struct> T.default(): T {
        if(this is VkSamplerCreateInfo) {
            this.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO).pNext(NULL)
        } else if(this is VkFramebufferCreateInfo) {
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

    private fun getBestDepthFormat(preferredFormat: Int): List<Int> {
        // this iterates through the list of possible (though not all required formats)
        // and returns the first one that is possible to use as a depth buffer on the
        // given physical device.
        val props = VkFormatProperties.calloc()
        val format = intArrayOf(
            preferredFormat,
            VK_FORMAT_D32_SFLOAT,
            VK_FORMAT_D32_SFLOAT_S8_UINT,
            VK_FORMAT_D24_UNORM_S8_UINT,
            VK_FORMAT_D16_UNORM_S8_UINT,
            VK_FORMAT_D16_UNORM
        ).filter {
            vkGetPhysicalDeviceFormatProperties(physicalDevice, it, props)

            props.optimalTilingFeatures() and VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT > 0
        }

        logger.debug("Using $format as depth format.")

        props.free()
        return format
    }

    fun colorAttachmentCount() = attachments.count { it.value.type == VulkanFramebufferType.COLOR_ATTACHMENT }

    fun depthAttachmentCount() = attachments.count { it.value.type == VulkanFramebufferType.DEPTH_ATTACHMENT }

    override fun close() {
        if(initialized) {
            attachments.values.forEach { it.close() }

            vkDestroyRenderPass(device, renderPass.get(0), null)
            memFree(renderPass)

            vkDestroySampler(device, framebufferSampler.get(0), null)
            memFree(framebufferSampler)

            vkDestroyFramebuffer(device, this.framebuffer.get(0), null)
            memFree(framebuffer)

            initialized = false
        }
    }
}

