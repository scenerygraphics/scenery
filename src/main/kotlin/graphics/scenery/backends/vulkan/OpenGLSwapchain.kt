package graphics.scenery.backends.vulkan

import graphics.scenery.backends.SceneryWindow
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.LongBuffer

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class OpenGLSwapchain(val window: SceneryWindow,
                      val device: VkDevice,
                      val physicalDevice: VkPhysicalDevice,
                      val queue: VkQueue,
                      val commandPool: Long,
                      val surface: Long,
                      val useSRGB: Boolean = true) : Swapchain {

    override var handle: Long = 0L
    override var images: LongArray? = null
    override var imageViews: LongArray? = null

    override var format: Int = 0

    override fun create(oldSwapchain: Swapchain?): Swapchain {
        val imageExtent = VkExtent3D.calloc()
            .set(window.width, window.height, 1)

        format = if (useSRGB) {
            VK10.VK_FORMAT_B8G8R8A8_SRGB
        } else {
            VK10.VK_FORMAT_B8G8R8A8_UNORM
        }

        val image = VkImageCreateInfo.calloc()
            .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .pNext(MemoryUtil.NULL)
            .imageType(VK10.VK_IMAGE_TYPE_2D)
            .extent(imageExtent)
            .mipLevels(1)
            .arrayLayers(1)
            .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
            .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
            .usage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK10.VK_IMAGE_USAGE_SAMPLED_BIT)

        val frontImage = MemoryUtil.memAllocLong(1)
        val backImage = MemoryUtil.memAllocLong(1)

        vkCreateImage(device, image, null, frontImage)
        vkCreateImage(device, image, null, backImage)

        images = longArrayOf(frontImage.get(0), backImage.get(0))

        with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
            imageViews = images!!.map { image ->
                VU.setImageLayout(
                    this@with,
                    image,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    oldImageLayout = VK_IMAGE_LAYOUT_UNDEFINED,
                    newImageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                )

                val imageView = MemoryUtil.memAllocLong(1)
                val subresourceRange = VkImageSubresourceRange.calloc()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1)

                val iv = VkImageViewCreateInfo.calloc()
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(format)
                    .subresourceRange(subresourceRange)
                    .image(image)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .pNext(MemoryUtil.NULL)

                vkCreateImageView(device, iv, null, imageView)

                imageView.get(0)
            }.toLongArray()

            this.endCommandBuffer(device, commandPool, queue,
                 flush = true, dealloc = true)
        }

        MemoryUtil.memFree(frontImage)
        MemoryUtil.memFree(backImage)

        handle = -1L

        return this
    }

    override fun present(waitForSemaphores: LongBuffer?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun next(timeout: Long, waitForSemaphore: Long): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun close() {

    }
}
