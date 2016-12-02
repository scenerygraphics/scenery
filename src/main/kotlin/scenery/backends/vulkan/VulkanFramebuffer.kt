package scenery.backends.vulkan

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
class VulkanFramebuffer(protected var device: VkDevice, protected var physicalDevice: VkPhysicalDevice, protected var commandPool: Long, width: Int, height: Int, commandBuffer: VkCommandBuffer) {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderer")

    var framebuffer  = memAllocLong(1)
    var renderPass = memAllocLong(1)
    var framebufferSampler = memAllocLong(1)
    var outputDescriptorSet: Long = -1L
    protected var commandBuffer: VkCommandBuffer

    var renderCommandBuffer: VulkanCommandBuffer? = null
    var semaphore: Long = -1L

    var width: Int = 0
        protected set

    var height: Int = 0
        protected set

    protected var initialized: Boolean = false

    enum class VulkanFramebufferType { COLOR_ATTACHMENT, DEPTH_ATTACHMENT }

    class VulkanFramebufferAttachment {
        var image: Long = 0
        var memory: LongBuffer = memAllocLong(1)
        var imageView: LongBuffer = memAllocLong(1)
        var format: Int = 0

        var type: VulkanFramebufferType = VulkanFramebufferType.COLOR_ATTACHMENT
        var desc: VkAttachmentDescription = VkAttachmentDescription.calloc()
    }

    var attachments = LinkedHashMap<String, VulkanFramebufferAttachment>()

    init {
        this.width = width
        this.height = height

        this.commandBuffer = commandBuffer
    }

    protected fun createAttachment(format: Int, usage: Int): VulkanFramebufferAttachment {
        val a = VulkanFramebufferAttachment()
        var aspectMask: Int = 0
        var imageLayout: Int = 0

        a.format = format

        if (usage == VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) {
            aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
            imageLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        }

        if (usage == VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) {
            aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT
            imageLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
        }

        var imageExtent = VkExtent3D.calloc()
            .width(width)
            .height(height)
            .depth(1)

        var image = VkImageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .pNext(NULL)
            .imageType(VK_IMAGE_TYPE_2D)
            .format(a.format)
            .extent(imageExtent)
            .mipLevels(1)
            .arrayLayers(1)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .tiling(VK_IMAGE_TILING_OPTIMAL)
            .usage(usage or VK_IMAGE_USAGE_SAMPLED_BIT)


        var images = memAllocLong(1)
        vkCreateImage(device, image, null, images)
        a.image = images.get(0)
        memFree(images)
        image.free()

        var requirements = VkMemoryRequirements.calloc()
        vkGetImageMemoryRequirements(device, a.image, requirements)

        var allocation = VkMemoryAllocateInfo.calloc()
            .allocationSize(requirements.size())
            .memoryTypeIndex(physicalDevice.getMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT).second)
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)

        vkAllocateMemory(device, allocation, null, a.memory)
        vkBindImageMemory(device, a.image, a.memory.get(0), 0)

        VU.setImageLayout(
            commandBuffer,
            a.image,
            aspectMask,
            VK_IMAGE_LAYOUT_UNDEFINED,
            if (usage == VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL else imageLayout
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

        return a
    }

    fun addFloatBuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format: Int = when(channelDepth) {
            16 -> VK_FORMAT_R16_SFLOAT
            32 -> VK_FORMAT_R32_SFLOAT
            else -> { System.err.println("Unsupported channel depth $channelDepth, using 16 bit."); VK_FORMAT_R16_SFLOAT }
        }

        val att = createAttachment(format, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
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
        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
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
        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
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
        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .format(format)

        attachments.put(name, att)

        return this
    }

    fun addUnsignedByteRGBABuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format: Int = when(channelDepth) {
            8 -> VK_FORMAT_R8G8B8A8_UNORM
            16 -> VK_FORMAT_R16G16B16A16_UNORM
            else -> { System.err.println("Unsupported channel depth $channelDepth, using 16 bit."); VK_FORMAT_R16G16B16A16_UINT }
        }

        val att = createAttachment(format, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
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
        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
            .format(bestSupportedFormat)

        att.type = VulkanFramebufferType.DEPTH_ATTACHMENT

        attachments.put(name, att)

        return this
    }

    fun addSwapchainAttachment(name: String, swapchain: VulkanRenderer.Swapchain, index: Int): VulkanFramebuffer {
        val att = VulkanFramebufferAttachment()

        att.image = swapchain.images!!.get(index)
        att.imageView.put(swapchain.imageViews!!.get(index))
        att.type = VulkanFramebufferType.COLOR_ATTACHMENT

        att.desc
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
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

        attachments.values.filter { it.type == VulkanFramebufferType.COLOR_ATTACHMENT }.forEachIndexed { i, att ->
            colorDescs[i]
                .attachment(i)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
        }

        val depthDescs = if(attachments.filter { it.value.type == VulkanFramebufferType.DEPTH_ATTACHMENT}.size > 0) {
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

        val renderPassInfo = VkRenderPassCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            .pAttachments(getAttachmentDescBuffer())
            .pSubpasses(subpass)
            .pDependencies(dependencyChain)
            .pNext(NULL)

        renderPass.put(0, VU.run(memAllocLong(1), "create renderpass")
            { vkCreateRenderPass(device, renderPassInfo, null, this) })

        logger.trace("Created renderpass ${renderPass.get(0)}")

        val fbinfo = VkFramebufferCreateInfo.calloc()
            .default()
            .renderPass(renderPass.get(0))
            .pAttachments(getAttachmentImageViews())
            .width(width)
            .height(height)
            .layers(1)

        framebuffer.put(0, VU.run(memAllocLong(1), "create framebuffer",
            { vkCreateFramebuffer(device, fbinfo, null, this) },
            { fbinfo.free() }))

        val sampler = VkSamplerCreateInfo.calloc()
            .default()
            .magFilter(VK_FILTER_LINEAR)
            .minFilter(VK_FILTER_LINEAR)
            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
            .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
            .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
            .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
            .mipLodBias(0.0f)
            .maxAnisotropy(0.0f)
            .minLod(0.0f)
            .maxLod(1.0f)
            .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)

        vkCreateSampler(device, sampler, null, this.framebufferSampler)

        sampler.free()
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
        val format = intArrayOf(
            preferredFormat,
            VK_FORMAT_D32_SFLOAT,
            VK_FORMAT_D32_SFLOAT_S8_UINT,
            VK_FORMAT_D24_UNORM_S8_UINT,
            VK_FORMAT_D16_UNORM_S8_UINT,
            VK_FORMAT_D16_UNORM
        ).filter {
            val props = VkFormatProperties.calloc()
            vkGetPhysicalDeviceFormatProperties(physicalDevice, it, props)

            props.optimalTilingFeatures() and VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT > 0
        }

        logger.info("Using $format as depth format.")

        return format;
    }

    fun colorAttachmentCount() = attachments.count { it.value.type == VulkanFramebufferType.COLOR_ATTACHMENT }

    fun depthAttachmentCount() = attachments.count { it.value.type == VulkanFramebufferType.DEPTH_ATTACHMENT }

    fun destroy() {
        vkDestroyFramebuffer(device, this.framebuffer.get(0), null)
    }
}

