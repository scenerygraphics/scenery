package graphics.scenery.backends.vulkan

import glfw_.appBuffer
import graphics.scenery.backends.vulkan.VU.setImageLayout
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import vkn.*
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

        val imageExtent = vk.Extent3D {
            width = this@VulkanFramebuffer.width
            height = this@VulkanFramebuffer.height
            depth = 1
        }

        val image = vk.ImageCreateInfo {
            imageType = VkImageType.`2D`
            this.format = att.format
            extent = imageExtent
            mipLevels = 1
            arrayLayers = 1
            samples = VkSampleCount.`1_BIT`
            tiling = VkImageTiling.OPTIMAL
            this.usage = usage or VkImageUsage.SAMPLED_BIT or VkImageUsage.TRANSFER_SRC_BIT or VkImageUsage.TRANSFER_DST_BIT
        }

        att.image = device.vulkanDevice createImage image

        val requirements = device.vulkanDevice getImageMemoryRequirements att.image

        val allocation = vk.MemoryAllocateInfo {
            allocationSize = requirements.size
            memoryTypeIndex(device.getMemoryType(requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)[0]) // TODO bug
        }

        att.memory = device.vulkanDevice allocateMemory allocation
        device.vulkanDevice.bindImageMemory(att.image, att.memory)

        commandBuffer.setImageLayout(
            att.image,
            aspectMask,
            VkImageLayout.UNDEFINED,
            when (usage) {
                VkImageUsage.COLOR_ATTACHMENT_BIT -> VkImageLayout.SHADER_READ_ONLY_OPTIMAL
                else -> imageLayout
            }
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
            this.image = att.image
        }

        att.imageView = device.vulkanDevice createImageView iv

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

        val (loadOp, stencilLoadOp) = when {
            !shouldClear -> VkAttachmentLoadOp.LOAD to VkAttachmentLoadOp.LOAD
            else -> VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.DONT_CARE
        }
        val initialImageLayout = when {
            !shouldClear -> VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            else -> VkImageLayout.UNDEFINED
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

        val (loadOp, stencilLoadOp) = when {
            !shouldClear -> VkAttachmentLoadOp.LOAD to VkAttachmentLoadOp.LOAD
            else -> VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.DONT_CARE
        }

        val initialImageLayout = when {
            !shouldClear -> VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            else -> VkImageLayout.UNDEFINED
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

        val (loadOp, stencilLoadOp) = when {
            !shouldClear -> VkAttachmentLoadOp.LOAD to VkAttachmentLoadOp.LOAD
            else -> VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.DONT_CARE
        }

        val initialImageLayout = when {
            !shouldClear -> VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            else -> VkImageLayout.UNDEFINED
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

        val (loadOp, stencilLoadOp) = when {
            !shouldClear -> VkAttachmentLoadOp.LOAD to VkAttachmentLoadOp.LOAD
            else -> VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.DONT_CARE
        }

        val initialImageLayout = when {
            !shouldClear -> VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            else -> VkImageLayout.UNDEFINED
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

        return this
    }

    fun addUnsignedByteRGBABuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format = when (channelDepth) {
            8 -> when {
                sRGB -> VkFormat.R8G8B8A8_SRGB
                else -> VkFormat.R8G8B8A8_UNORM
            }
            16 -> VkFormat.R16G16B16A16_UNORM
            else -> {
                logger.warn("Unsupported channel depth $channelDepth, using 16 bit."); VkFormat.R16G16B16A16_UINT
            }
        }

        val att = createAttachment(format, VkImageUsage.COLOR_ATTACHMENT_BIT)

        val (loadOp, stencilLoadOp) = when {
            !shouldClear -> VkAttachmentLoadOp.LOAD to VkAttachmentLoadOp.LOAD
            else -> VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.DONT_CARE
        }

        val initialImageLayout = when {
            !shouldClear -> VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            else -> VkImageLayout.UNDEFINED
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

        val (loadOp, stencilLoadOp) = when {
            !shouldClear -> VkAttachmentLoadOp.LOAD to VkAttachmentLoadOp.LOAD
            else -> VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.DONT_CARE
        }

        val initialImageLayout = when {
            !shouldClear -> VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            else -> VkImageLayout.UNDEFINED
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

        val (loadOp, stencilLoadOp) = when {
            !shouldClear -> VkAttachmentLoadOp.DONT_CARE to VkAttachmentLoadOp.LOAD
            else -> VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.CLEAR
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
            this.format = bestSupportedFormat
        }
        att.type = VkFramebufferAttachment.DEPTH

        attachments[name] = att

        return this
    }

    fun addSwapchainAttachment(name: String, swapchain: Swapchain, index: Int): VulkanFramebuffer {
        val att = VulkanFramebufferAttachment()

        att.image = swapchain.images!![index]
        att.imageView = swapchain.imageViews!![index]
        att.type = VkFramebufferAttachment.COLOR
        att.fromSwapchain = true

        val (loadOp, stencilLoadOp) = when {
            !shouldClear -> VkAttachmentLoadOp.LOAD to VkAttachmentLoadOp.LOAD
            else -> VkAttachmentLoadOp.CLEAR to VkAttachmentLoadOp.DONT_CARE
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

    protected fun getAttachmentDescBuffer(): VkAttachmentDescription.Buffer {
        val descriptionBuffer = vk.AttachmentDescription(attachments.size)
        for (i in 0 until attachments.size)
            descriptionBuffer[i] = attachments.values.elementAt(i).desc
        return descriptionBuffer
    }

    fun createRenderpassAndFramebuffer() {

        val colorDescs = vk.AttachmentReference(attachments.filter { it.value.type == VkFramebufferAttachment.COLOR }.size)

        attachments.values.filter { it.type == VkFramebufferAttachment.COLOR }.forEachIndexed { i, _ ->
            colorDescs[i].apply {
                attachment = i
                layout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            }
        }

        val depthDescs: VkAttachmentReference? = vk.AttachmentReference {
            attachment = colorDescs.limit()
            layout = VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL
        }.takeIf { attachments.any { it.value.type == VkFramebufferAttachment.DEPTH } }


        logger.trace("Subpass for has ${colorDescs.remaining()} color attachments")

        val subpass = vk.SubpassDescription(1) {
            colorAttachments = colorDescs
            colorAttachmentCount = colorDescs.remaining()
            depthStencilAttachment = depthDescs
            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
        }

        val dependencyChain = vk.SubpassDependency(2)
            .appyAt(0) {
                srcSubpass = VK_SUBPASS_EXTERNAL
                dstSubpass = 0
                srcStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
                dstStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
                srcAccessMask = VkAccess.MEMORY_READ_BIT.i
                dstAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
                dependencyFlags = VkDependency.BY_REGION_BIT.i
            }
            .appyAt(1) {
                srcSubpass = 0
                dstSubpass = VK_SUBPASS_EXTERNAL
                srcStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
                dstStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
                srcAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
                dstAccessMask = VkAccess.MEMORY_READ_BIT.i
                dependencyFlags = VkDependency.BY_REGION_BIT.i
            }
        val attachmentDescs = getAttachmentDescBuffer()
        val renderPassInfo = VkRenderPassCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            .pAttachments(attachmentDescs)
            .pSubpasses(subpass)
            .pDependencies(dependencyChain)
            .pNext(NULL)

        renderPass = device.vulkanDevice createRenderPass renderPassInfo

        logger.trace("Created renderpass $renderPass")

        val fbinfo = vk.FramebufferCreateInfo {
            renderPass = this@VulkanFramebuffer.renderPass
            attachments = appBuffer.longBufferOf(this@VulkanFramebuffer.attachments.values.map { it.imageView })
            width = this@VulkanFramebuffer.width
            height = this@VulkanFramebuffer.height
            layers = 1
        }
        framebuffer = device createFramebuffer fbinfo

        val samplerCreateInfo = vk.SamplerCreateInfo {
            magFilter = VkFilter.LINEAR
            minFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            addressModeU = VkSamplerAddressMode.CLAMP_TO_EDGE
            addressModeV = VkSamplerAddressMode.CLAMP_TO_EDGE
            addressModeW = VkSamplerAddressMode.CLAMP_TO_EDGE
            mipLodBias = 0f
            maxAnisotropy = 1f
            minLod = 0f
            maxLod = 1f
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
        }
        framebufferSampler = device.vulkanDevice createSampler samplerCreateInfo

        initialized = true
    }

    override fun toString(): String {
        return "VulkanFramebuffer"
    }

    val id: Int get() = if (initialized) 0 else -1

    private fun getBestDepthFormat(preferredFormat: VkFormat): List<VkFormat> {
        // this iterates through the list of possible (though not all required formats)
        // and returns the first one that is possible to use as a depth buffer on the
        // given physical device.
        val props = vk.FormatProperties { }
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

    fun colorAttachmentCount() = attachments.count { it.value.type == VkFramebufferAttachment.COLOR }

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

