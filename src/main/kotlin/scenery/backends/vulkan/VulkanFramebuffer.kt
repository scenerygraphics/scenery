package scenery.backends.vulkan

import org.lwjgl.vulkan.*
import java.nio.IntBuffer
import java.util.*
import org.lwjgl.vulkan.VK10.*
import java.util.ArrayList
import scenery.backends.vulkan.*
import java.nio.LongBuffer
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.Struct

/**
 *

 * @author Ulrik Günther @ulrik.is>
 */
class VulkanFramebuffer(protected var device: VkDevice, protected var physicalDevice: VkPhysicalDevice, width: Int, height: Int, commandBuffer: VkCommandBuffer) {
    protected var framebuffer: Long = 0
    protected var renderPass: Long = 0
    protected var commandBuffer: VkCommandBuffer

    var width: Int = 0
        protected set

    var height: Int = 0
        protected set

    protected var initialized: Boolean = false

    protected class VulkanFramebufferAttachment {
        var image: Long = 0
        var memory: LongBuffer = memAllocLong(1)
        var imageView: LongBuffer = memAllocLong(1)
        var format: Int = 0
    }

    protected var attachments = ArrayList<Pair<String, VulkanFramebufferAttachment>>()

    init {
        this.width = width
        this.height = height

        this.commandBuffer = commandBuffer
    }

    fun addAttachment(format: Int, usage: Int) {
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

        var requirements = VkMemoryRequirements.calloc()
        vkGetImageMemoryRequirements(device, a.image, requirements)

        var allocation = VkMemoryAllocateInfo.calloc()
            .allocationSize(requirements.size())
            .memoryTypeIndex(physicalDevice.getMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT).second)

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

        vkCreateImageView(device, iv, null, a.imageView)
    }

    fun addFloatBuffer(channelDepth: Int) {
    }

    fun addFloatRGBBuffer(channelDepth: Int) {
    }

    fun addUnsignedByteRGBABuffer(channelDepth: Int) {
    }

    @JvmOverloads fun addDepthBuffer(depth: Int, scale: Int = 1) {
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
}

