package scenery.backends.vulkan

import org.lwjgl.vulkan.*
import java.nio.IntBuffer
import java.util.*
import org.lwjgl.vulkan.VK10.*
import java.util.ArrayList
import scenery.backends.vulkan.*
import java.nio.LongBuffer
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.NativeResource
import org.lwjgl.system.Struct

/**
 *

 * @author Ulrik Günther @ulrik.is>
 */
class VulkanFramebuffer(protected var device: VkDevice, protected var physicalDevice: VkPhysicalDevice, width: Int, height: Int, commandBuffer: VkCommandBuffer) {
    protected var framebuffer  = memAllocLong(1)
    protected var renderPass = memAllocLong(1)
    protected var framebufferSampler = memAllocLong(1)
    protected var commandBuffer: VkCommandBuffer

    var width: Int = 0
        protected set

    var height: Int = 0
        protected set

    protected var initialized: Boolean = false

    protected enum class VulkanFramebufferType { COLOR_ATTACHMENT, DEPTH_ATTACHMENT }

    protected class VulkanFramebufferAttachment {
        var image: Long = 0
        var memory: LongBuffer = memAllocLong(1)
        var imageView: LongBuffer = memAllocLong(1)
        var format: Int = 0
        var type: VulkanFramebufferType = VulkanFramebufferType.COLOR_ATTACHMENT

        var desc: VkAttachmentDescription = VkAttachmentDescription.calloc()
    }

    protected var attachments = LinkedHashMap<String, VulkanFramebufferAttachment>()

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

        if (usage and VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT == 1) {
            aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
            imageLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        }

        if (usage and VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT == 1) {
            aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT or VK_IMAGE_ASPECT_STENCIL_BIT
            imageLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
        }

        var imageExtent = VkExtent3D.calloc()
            .width(width)
            .height(height)
            .depth(1)

        var image = VkImageCreateInfo.calloc()
            .imageType(VK_IMAGE_TYPE_2D)
            .format(a.format)
            .extent(imageExtent)
            .mipLevels(1)
            .arrayLayers(1)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .tiling(VK_IMAGE_TILING_OPTIMAL)
            .usage(usage or VK_IMAGE_USAGE_SAMPLED_BIT)
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .pNext(NULL)

        var images = memAllocLong(1)
        vkCreateImage(device, image, null, images)
        a.image = images.get(0)
        memFree(images)

        var requirements = VkMemoryRequirements.calloc()
        vkGetImageMemoryRequirements(device, a.image, requirements)

        var allocation = VkMemoryAllocateInfo.calloc()
            .allocationSize(requirements.size())
            .memoryTypeIndex(physicalDevice.getMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT).second)
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)

        vkAllocateMemory(device, allocation, null, a.memory)
        vkBindImageMemory(device, a.image, a.memory.get(0), 0)

        VulkanUtils.setImageLayout(
            commandBuffer,
            a.image,
            aspectMask,
            VK_IMAGE_LAYOUT_UNDEFINED,
            if (usage and VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT == 1) VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL else imageLayout
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

        attachments.put(name, att)

        return this
    }

    fun addUnsignedByteRGBABuffer(name: String, channelDepth: Int): VulkanFramebuffer {
        val format: Int = when(channelDepth) {
            16 -> VK_FORMAT_R16G16B16A16_UINT
            32 -> VK_FORMAT_R32G32B32A32_UINT
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

        attachments.put(name, att)

        return this
    }

    fun addDepthBuffer(name: String, depth: Int): VulkanFramebuffer {
        val format: Int = when(depth) {
            24 -> VK_FORMAT_D16_UNORM
            32 -> VK_FORMAT_D32_SFLOAT
            else -> { System.err.println("Unsupported channel depth $depth, using 32 bit."); VK_FORMAT_D32_SFLOAT }
        }

        val att = createAttachment(getSupportedDepthFormats().first(), VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
        att.desc.samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)

        att.type = VulkanFramebufferType.DEPTH_ATTACHMENT

        attachments.put(name, att)

        return this
    }

    fun checkDrawBuffers(): Boolean {
        return true
    }

    fun setDrawBuffers() {
    }

    fun bindTexturesToUnitsWithOffset(offset: Int) {
    }

    fun getTextureIds(): List<Int> {
        return listOf(0)
    }

    fun revertToDefaultFramebuffer() {
    }

    fun resize(newWidth: Int, newHeight: Int) {
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

    fun createPassAndFramebuffer() {
        val colorDescs = VkAttachmentReference.calloc(attachments.filter { it.value.type == VulkanFramebufferType.COLOR_ATTACHMENT }.size)

        attachments.filter { it.value.type == VulkanFramebufferType.COLOR_ATTACHMENT }.values.forEachIndexed { i, att ->
            colorDescs[i]
                .attachment(i)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
        }
        val depthDescs = if(attachments.filter { it.value.type == VulkanFramebufferType.DEPTH_ATTACHMENT}.size > 0) {
            VkAttachmentReference.calloc()
                .attachment(colorDescs.limit())
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
        } else {
            null
        }

        val subpass = VkSubpassDescription.calloc(1)
            .pColorAttachments(colorDescs)
            .pDepthStencilAttachment(depthDescs)

        val dependencyChain = VkSubpassDependency.calloc(2)
        dependencyChain[0].srcSubpass(VK_SUBPASS_EXTERNAL)
            .dstSubpass(0)
            .srcStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
            .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
            .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)

        dependencyChain[1].srcSubpass(0)
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

        vkCreateRenderPass(device, renderPassInfo, null, renderPass)

        val fbinfo = VkFramebufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
            .pNext(NULL)
            .renderPass(renderPass.get(0))
            .pAttachments(getAttachmentImageViews())
            .width(width)
            .height(height)
            .layers(1)

        vkCreateFramebuffer(device, fbinfo, null, framebuffer)

        val sampler = VkSamplerCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            .pNext(NULL)
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
    }

    fun Struct.default(): Struct {
        if(this is VkSamplerCreateInfo) {
            this.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO).pNext(NULL)
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

    val boundBufferNum: Int = 0

    private val currentFramebufferColorAttachment: Int = 0

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

    fun Struct.allocWith(): Struct? {
        return null
    }

    private fun getSupportedDepthFormats(): List<Int> {
        // this iterates through the list of possible (though not all required formats)
        // and returns the first one that is possible to use as a depth buffer on the
        // given physical device.
        return intArrayOf(
            VK_FORMAT_D32_SFLOAT_S8_UINT,
            VK_FORMAT_D32_SFLOAT,
            VK_FORMAT_D24_UNORM_S8_UINT,
            VK_FORMAT_D16_UNORM_S8_UINT,
            VK_FORMAT_D16_UNORM
        ).filter {
            val props = VkFormatProperties.calloc()
            vkGetPhysicalDeviceFormatProperties(physicalDevice, it, props)

            props.optimalTilingFeatures() and VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT > 0
        }
    }
}

