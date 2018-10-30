package graphics.scenery.backends.vulkan

import glm_.size
import graphics.scenery.utils.LazyLogger
import kool.cap
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.*
import java.util.*
import org.lwjgl.vulkan.VK10.*
import vkk.*
import vkk.`object`.*

/**
 * Vulkan Framebuffer class. Creates a framebuffer on [device], associated with
 * a [commandPool]. The framebuffer's [width] and [height] need to be given, as well
 * as a [commandBuffer] during which's execution the framebuffer will be created.
 *
 * [shouldClear] - set if on the beginning of a render pass, the framebuffer should be cleared. On by default.
 * [sRGB] - set if sRGB targets should be used. Off by default.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanFramebuffer(protected val device: VulkanDevice,
                             protected var commandPool: VkCommandPool,
                             var width: Int,
                             var height: Int,
                             val commandBuffer: VkCommandBuffer,
                             var shouldClear: Boolean = true, val sRGB: Boolean = false) : AutoCloseable {
    protected val logger by LazyLogger()

    /** Raw Vulkan framebuffer reference. */
    var framebuffer = VkFramebuffer(NULL)
        protected set

    /** Raw Vulkan renderpass reference. */
    var renderPass = VkRenderPass(NULL)
        protected set

    /* Raw Vulkan sampler reference. */
    var framebufferSampler = VkSampler(NULL)
        protected set

    /** Descriptor set for this framebuffer's output.  */
    var outputDescriptorSet = VkDescriptorSet(NULL)
        internal set

    /** Flag to indicate whether this framebuffer has been initialiased or not. */
    protected var initialized: Boolean = false

    /** Enum class for indicating whether a framebuffer containts a color or a depth attachment. */
    enum class VulkanFramebufferType { COLOR_ATTACHMENT, DEPTH_ATTACHMENT }

    /** Class to describe framebuffer attachments */
    inner class VulkanFramebufferAttachment : AutoCloseable {
        /** Image reference for the attachment */
        var image = VkImage(NULL)
        /** Memory reference for the attachment */
        var memory = VkDeviceMemory(NULL)
        /** Image view for the attachment */
        var imageView = VkImageView(NULL)
        /** Vulkan format of this attachment */
        var format = VkFormat.UNDEFINED

        /** Attachment type */
        var type: VulkanFramebufferType = VulkanFramebufferType.COLOR_ATTACHMENT

        /** Vulkan attachment description */
        var desc: VkAttachmentDescription = VkAttachmentDescription.calloc()

        /**
         * Indicates whether the image for this attachment comes from a swapchain image,
         * in which case a dedicated allocation is not necessary.
         */
        var fromSwapchain = false

        /**
         * Closes the attachment, freeing its resources.
         */
        override fun close() {
            vkDev.apply {
                destroyImageView(imageView)

                if (image.L != NULL && !fromSwapchain) {
                    destroyImage(image)
                }

                if (memory.L != NULL) {
                    freeMemory(memory)
                }

                desc.free()
            }
        }
    }

    /** Linked hash map of this framebuffer's [VulkanFramebufferAttachment]s. */
    var attachments = LinkedHashMap<String, VulkanFramebufferAttachment>()

    val vkDev get() = device.vulkanDevice

    /**
     * Internal function to create attachments of [format], with image usage flags given in [usage].
     * The attachment will have dimensions [attachmentWidth] x [attachmentHeight].
     *
     * This function also creates the necessary images, memory allocs, and image views.
     */
    protected fun createAttachment(format: VkFormat, usage: VkImageUsage, attachmentWidth: Int = width, attachmentHeight: Int = height): VulkanFramebufferAttachment {
        val a = VulkanFramebufferAttachment()
        var aspectMask: VkImageAspectFlags = 0
        var imageLayout = VkImageLayout.UNDEFINED

        a.format = format

        if (usage == VkImageUsage.COLOR_ATTACHMENT_BIT) {
            aspectMask = VkImageAspect.COLOR_BIT.i
            imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
        }

        if (usage == VkImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT) {
            aspectMask = VkImageAspect.DEPTH_BIT.i
            imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
        }

        val imageExtent = vk.Extent3D {
            width = attachmentWidth
            height = attachmentHeight
            depth = 1
        }
        val image = vk.ImageCreateInfo {
            imageType = VkImageType.`2D`
            this.format = a.format
            extent = imageExtent
            mipLevels = 1
            arrayLayers = 1
            samples = VkSampleCount.`1_BIT`
            tiling = VkImageTiling.OPTIMAL
            this.usage = usage or VkImageUsage.SAMPLED_BIT or VkImageUsage.TRANSFER_SRC_BIT or VkImageUsage.TRANSFER_DST_BIT
        }

        a.image = vkDev createImage image

        val requirements = vkDev getImageMemoryRequirements a.image

        val allocation = vk.MemoryAllocateInfo {
            allocationSize = requirements.size
            memoryTypeIndex = device.getMemoryType(requirements.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT.i).first()
        }

        a.memory = vkDev allocateMemory allocation
        vkDev.bindImageMemory(a.image, a.memory)

        VU.setImageLayout(
            commandBuffer,
            a.image,
            aspectMask,
            VkImageLayout.UNDEFINED,
            if (usage == VkImageUsage.COLOR_ATTACHMENT_BIT) VkImageLayout.SHADER_READ_ONLY_OPTIMAL
            else imageLayout
        )

        val subresourceRange = vk.ImageSubresourceRange {
            this.aspectMask = aspectMask
            baseMipLevel = 0
            levelCount = 1
            baseArrayLayer = 0
            layerCount = 1
        }
        val iv = vk.ImageViewCreateInfo {
            viewType = VkImageViewType.`2D`
            this.format = format
            this.subresourceRange = subresourceRange
            this.image = a.image
        }
        a.imageView = vkDev createImageView iv

        return a
    }

    /**
     * Internal function to create a depth/stencil attachment of [format], with
     * dimensions [attachmentWidth] x [attachmentHeight].
     */
    private fun createAndAddDepthStencilAttachmentInternal(name: String, format: VkFormat, attachmentWidth: Int, attachmentHeight: Int) {
        val att = createAttachment(format, VkImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT, attachmentWidth, attachmentHeight)

        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VkAttachmentLoadOp.DONT_CARE to VkAttachmentLoadOp.LOAD
        } else {
            VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.CLEAR
        }

        val initialImageLayout = VkImageLayout.UNDEFINED

        att.desc.apply {
            samples = VkSampleCount.`1_BIT`
            this.loadOp = loadOp
            storeOp = VkAttachmentStoreOp.STORE
            this.stencilLoadOp = stencilLoadOp
            stencilStoreOp = VkAttachmentStoreOp.STORE
            initialLayout = initialImageLayout
            finalLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
            this.format = format
        }
        att.type = VulkanFramebufferType.DEPTH_ATTACHMENT

        attachments[name] = att
    }

    /**
     * Internal function to create a new color attachment of format [fornat], with
     * dimensions [attachmentWidth] x [attachmentHeight].
     */
    private fun createAndAddColorAttachmentInternal(name: String, format: VkFormat, attachmentWidth: Int, attachmentHeight: Int) {
        val att = createAttachment(format, VkImageUsage.COLOR_ATTACHMENT_BIT, attachmentWidth, attachmentHeight)

        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VkAttachmentLoadOp.LOAD to VkAttachmentLoadOp.LOAD
        } else {
            VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.DONT_CARE
        }

        val initialImageLayout = if (!shouldClear) {
            VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
        } else {
            VkImageLayout.UNDEFINED
        }

        att.desc.apply {
            samples = VkSampleCount.`1_BIT`
            this.loadOp = loadOp
            storeOp = VkAttachmentStoreOp.STORE
            this.stencilLoadOp = stencilLoadOp
            stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
            initialLayout = initialImageLayout
            finalLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
            this.format = format
        }
        attachments[name] = att
    }

    /**
     * Adds a float attachment with a bit depth of [channelDepth], and a size of [attachmentWidth] x [attachmentHeight].
     */
    fun addFloatBuffer(name: String, channelDepth: Int, attachmentWidth: Int = width, attachmentHeight: Int = height): VulkanFramebuffer {
        val format = when (channelDepth) {
            16 -> VkFormat.R16_SFLOAT
            32 -> VkFormat.R32_SFLOAT
            else -> VkFormat.R16_SFLOAT.also {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit.")
            }
        }

        createAndAddColorAttachmentInternal(name, format, attachmentWidth, attachmentHeight)

        return this
    }

    /**
     * Adds a float RG attachment with a bit depth of [channelDepth], and a size of [attachmentWidth] x [attachmentHeight].
     */
    fun addFloatRGBuffer(name: String, channelDepth: Int, attachmentWidth: Int = width, attachmentHeight: Int = height): VulkanFramebuffer {
        val format = when (channelDepth) {
            16 -> VkFormat.R16G16_SFLOAT
            32 -> VkFormat.R32G32_SFLOAT
            else -> VkFormat.R16G16_SFLOAT.also {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit.")
            }
        }

        createAndAddColorAttachmentInternal(name, format, attachmentWidth, attachmentHeight)

        return this
    }

    /**
     * Adds a float RGB attachment with a bit depth of [channelDepth], and a size of [attachmentWidth] x [attachmentHeight].
     */
    fun addFloatRGBBuffer(name: String, channelDepth: Int, attachmentWidth: Int = width, attachmentHeight: Int = height): VulkanFramebuffer {
        val format = when (channelDepth) {
            16 -> VkFormat.R16G16B16_SFLOAT
            32 -> VkFormat.R32G32B32_SFLOAT
            else -> VkFormat.R16G16B16A16_SFLOAT.also {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit.")
            }
        }

        createAndAddColorAttachmentInternal(name, format, attachmentWidth, attachmentHeight)

        return this
    }

    /**
     * Adds a float RGBA attachment with a bit depth of [channelDepth], and a size of [attachmentWidth] x [attachmentHeight].
     */
    fun addFloatRGBABuffer(name: String, channelDepth: Int, attachmentWidth: Int = width, attachmentHeight: Int = height): VulkanFramebuffer {
        val format = when (channelDepth) {
            16 -> VkFormat.R16G16B16A16_SFLOAT
            32 -> VkFormat.R32G32B32A32_SFLOAT
            else -> VkFormat.R16G16B16A16_SFLOAT.also {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit.")
            }
        }

        createAndAddColorAttachmentInternal(name, format, attachmentWidth, attachmentHeight)

        return this
    }

    /**
     * Adds an unsigned byte RGBA attachment with a bit depth of [channelDepth], and a size of [attachmentWidth] x [attachmentHeight].
     */
    fun addUnsignedByteRGBABuffer(name: String, channelDepth: Int, attachmentWidth: Int = width, attachmentHeight: Int = height): VulkanFramebuffer {
        val format = when (channelDepth) {
            8 -> if (sRGB) {
                VkFormat.R8G8B8A8_SRGB
            } else {
                VkFormat.R8G8B8A8_UNORM
            }
            16 -> VkFormat.R16G16B16A16_UNORM
            else -> VkFormat.R16G16B16A16_UINT.also {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit.")
            }
        }

        createAndAddColorAttachmentInternal(name, format, attachmentWidth, attachmentHeight)

        return this
    }

    /**
     * Adds an unsigned byte R attachment with a bit depth of [channelDepth], and a size of [attachmentWidth] x [attachmentHeight].
     */
    fun addUnsignedByteRBuffer(name: String, channelDepth: Int, attachmentWidth: Int = width, attachmentHeight: Int = height): VulkanFramebuffer {
        val format = when (channelDepth) {
            8 -> VkFormat.R8_UNORM
            16 -> VkFormat.R16_UNORM
            else -> VkFormat.R16_UNORM.also {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit.")
            }
        }

        createAndAddColorAttachmentInternal(name, format, attachmentWidth, attachmentHeight)

        return this
    }

    /**
     * Adds a depth buffer attachment with a bit depth of [depth], and a size of [attachmentWidth] x [attachmentHeight].
     */
    fun addDepthBuffer(name: String, depth: Int, attachmentWidth: Int = width, attachmentHeight: Int = height): VulkanFramebuffer {
        val format = when (depth) {
            16 -> VkFormat.D16_UNORM
            24 -> VkFormat.D24_UNORM_S8_UINT
            32 -> VkFormat.D32_SFLOAT
            else -> VkFormat.D32_SFLOAT.also {
                logger.warn("Unsupported channel depth $depth, using 32 bit.")
            }
        }

        val bestSupportedFormat = getBestDepthFormat(format).first()

        createAndAddDepthStencilAttachmentInternal(name, bestSupportedFormat, attachmentWidth, attachmentHeight)

        return this
    }

    /**
     * Adds a swapchain-based attachment from the given [swapchain]. The image will be derived
     * from the swapchain's image [index].
     */
    fun addSwapchainAttachment(name: String, swapchain: Swapchain, index: Int): VulkanFramebuffer {
        val att = VulkanFramebufferAttachment().apply {
            image = swapchain.images[index]
            imageView = swapchain.imageViews[index]
            type = VulkanFramebufferType.COLOR_ATTACHMENT
            fromSwapchain = true
        }
        val (loadOp, stencilLoadOp) = if (!shouldClear) {
            VkAttachmentLoadOp.LOAD to VkAttachmentLoadOp.LOAD
        } else {
            VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.DONT_CARE
        }

        val initialImageLayout = VkImageLayout.UNDEFINED

        att.desc.apply {
            samples = VkSampleCount.`1_BIT`
            this.loadOp = loadOp
            storeOp = VkAttachmentStoreOp.STORE
            this.stencilLoadOp = stencilLoadOp
            stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
            initialLayout = initialImageLayout
            finalLayout = VkImageLayout.PRESENT_SRC_KHR
            format = when {
                sRGB -> VkFormat.B8G8R8A8_SRGB
                else -> VkFormat.B8G8R8A8_UNORM
            }
        }
        attachments[name] = att

        return this
    }

    /**
     * Gets a Vulkan attachment description from the current framebuffer state.
     */
    protected fun getAttachmentDescBuffer(): VkAttachmentDescription.Buffer {
        return vk.AttachmentDescription(attachments.size) {
            attachments.values.elementAt(it).desc
        }
    }

    /**
     * Gets all the image views of the current framebuffer.
     */
    protected fun getAttachmentImageViews(): VkImageViewBuffer {
        return vk.ImageViewBuffer(attachments.size) {
            attachments.values.elementAt(it).imageView
        }
    }

    /**
     * Creates the Vulkan Renderpass and Framebuffer from the state of the
     * framebuffer.
     */
    fun createRenderpassAndFramebuffer() {
        val colors = attachments.values.filter { it.type == VulkanFramebufferType.COLOR_ATTACHMENT }
        val colorDescs = vk.AttachmentReference(colors.size)

        for (i in colors.indices)
            colorDescs[i].apply {
                attachment = i
                layout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            }

        logger.trace("Subpass for has ${colorDescs.remaining()} color attachments")

        val subpass = vk.SubpassDescription {
            colorAttachments = colorDescs
            colorAttachmentCount = colorDescs.rem
            if (attachments.any { it.value.type == VulkanFramebufferType.DEPTH_ATTACHMENT })
                depthStencilAttachment = vk.AttachmentReference {
                    attachment = colorDescs.lim
                    layout = VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                }
            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
        }

        val dependencyChain = vk.SubpassDependency(2).also {
            it[0].apply {
                srcSubpass = VK_SUBPASS_EXTERNAL
                dstSubpass = 0
                srcStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
                dstStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
                srcAccessMask = VkAccess.MEMORY_READ_BIT.i
                dstAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT.i or VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                dependencyFlags = VkDependency.BY_REGION_BIT.i
            }
            it[1].apply {
                srcSubpass = 0
                dstSubpass = VK_SUBPASS_EXTERNAL
                srcStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
                dstStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
                srcAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
                dstAccessMask = VkAccess.MEMORY_READ_BIT.i
                dependencyFlags = VkDependency.BY_REGION_BIT.i
            }
        }
        val attachmentDescs = getAttachmentDescBuffer()
        val renderPassInfo = vk.RenderPassCreateInfo {
            attachments = attachmentDescs
            this.subpass = subpass
            dependencies = dependencyChain
        }

        renderPass = vkDev createRenderPass renderPassInfo

        logger.trace("Created renderpass $renderPass")

        val attachmentImageViews = getAttachmentImageViews()
        val fbinfo = vk.FramebufferCreateInfo {
            renderPass = this@VulkanFramebuffer.renderPass
            attachments = attachmentImageViews
            width = this@VulkanFramebuffer.width
            height = this@VulkanFramebuffer.height
            layers = 1
        }
        framebuffer = vkDev createFramebuffer fbinfo

        val samplerCreateInfo = vk.SamplerCreateInfo {
            minMagFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            addressModeUVW = VkSamplerAddressMode.CLAMP_TO_EDGE
            mipLodBias = 0f
            maxAnisotropy = 1f
            minLod = 0f
            maxLod = 1f
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
        }

        framebufferSampler = vkDev createSampler samplerCreateInfo

        initialized = true
    }

    /** Returns a string representation of this framebuffer. */
    override fun toString(): String {
        return "VulkanFramebuffer ${width}x$height (${attachments.size} attachments)"
    }

    /**
     * Returns the best available depth format, from a list of [preferredFormat]s.
     */
    private fun getBestDepthFormat(preferredFormat: VkFormat): List<VkFormat> {
        // this iterates through the list of possible (though not all required formats)
        // and returns the first one that is possible to use as a depth buffer on the
        // given physical device.
        val props = vk.FormatProperties()
        val format = arrayOf(
            preferredFormat,
            VkFormat.D32_SFLOAT,
            VkFormat.D32_SFLOAT_S8_UINT,
            VkFormat.D24_UNORM_S8_UINT,
            VkFormat.D16_UNORM_S8_UINT,
            VkFormat.D16_UNORM
        ).filter {
            device.physicalDevice.getFormatProperties(it, props)

            props.optimalTilingFeatures has VkFormatFeature.DEPTH_STENCIL_ATTACHMENT_BIT
        }

        logger.debug("Using $format as depth format.")

        return format
    }

    /** Returns the number of current color attachments. */
    fun colorAttachmentCount() = attachments.count { it.value.type == VulkanFramebufferType.COLOR_ATTACHMENT }

    /** Returns the number of current depth attachments. */
    fun depthAttachmentCount() = attachments.count { it.value.type == VulkanFramebufferType.DEPTH_ATTACHMENT }

    /** Closes this framebuffer instance, releasing all of its resources. */
    override fun close() {
        if (initialized) {
            attachments.values.forEach { it.close() }

            vkDev.apply {
                destroyRenderPass(renderPass)
                destroySampler(framebufferSampler)
                destroyFramebuffer(framebuffer)
            }

            initialized = false
        }
    }
}

