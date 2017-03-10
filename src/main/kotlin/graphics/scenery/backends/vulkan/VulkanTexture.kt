package graphics.scenery.backends.vulkan

import cleargl.TGAReader
import org.lwjgl.vulkan.VkImageCreateInfo
import java.awt.Color
import java.awt.color.ColorSpace
import java.awt.geom.AffineTransform
import java.awt.image.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.imageio.ImageIO
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Files

/**
 * Created by ulrik on 11/1/2016.
 */
class VulkanTexture(val device: VkDevice, val physicalDevice: VkPhysicalDevice,
                    val memoryProperties: VkPhysicalDeviceMemoryProperties,
                    val commandPool: Long, val queue: VkQueue,
                    val width: Int, val height: Int, val depth: Int = 1,
                    val format: Int = VK_FORMAT_R8G8B8_SRGB, val mipLevels: Int = 1) : AutoCloseable {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderer")

    var image: VulkanImage? = null
    private var stagingImage: VulkanImage

    inner class VulkanImage(var image: Long = -1L, var memory: Long = -1L, val maxSize: Long = -1L) {

        var sampler: Long = -1L
        var view: Long = -1L


        fun copyFrom(commandBuffer: VkCommandBuffer, buffer: VulkanBuffer) {
            with(commandBuffer) {
                val bufferImageCopy = VkBufferImageCopy.calloc(1)

                bufferImageCopy.imageSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)

                bufferImageCopy.imageExtent()
                    .width(width)
                    .height(height)
                    .depth(1)

                vkCmdCopyBufferToImage(this,
                    buffer.buffer,
                    this@VulkanImage.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    bufferImageCopy)

                bufferImageCopy.free()
            }
        }

        fun copyFrom(commandBuffer: VkCommandBuffer, image: VulkanImage) {
            with(commandBuffer) {
                val subresource = VkImageSubresourceLayers.calloc()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseArrayLayer(0)
                    .mipLevel(0)
                    .layerCount(1)

                val region = VkImageCopy.calloc(1)
                    .srcSubresource(subresource)
                    .dstSubresource(subresource)

                region.srcOffset().set(0, 0, 0)
                region.dstOffset().set(0, 0, 0)
                region.extent().set(width, height, depth)

                vkCmdCopyImage(this,
                    image.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    this@VulkanImage.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region)

                subresource.free()
                region.free()
            }
        }
    }

    init {
        stagingImage = createImage(width, height, depth,
            format, VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
            VK_IMAGE_TILING_LINEAR,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_CACHED_BIT,
            mipLevels = 1)
    }

    private fun createImage(width: Int, height: Int, depth: Int, format: Int, usage: Int, tiling: Int, memoryFlags: Int, mipLevels: Int): VulkanImage {
        val extent = VkExtent3D.calloc().set(width, height, depth)
        val imageInfo = VkImageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .pNext(NULL)
            .imageType(if (depth == 1) {
                VK_IMAGE_TYPE_2D
            } else {
                VK_IMAGE_TYPE_3D
            })
            .extent(extent)
            .mipLevels(mipLevels)
            .arrayLayers(1)
            .format(format)
            .tiling(tiling)
            .initialLayout(VK_IMAGE_LAYOUT_PREINITIALIZED)
            .usage(usage)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .flags(VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT)

        val reqs = VkMemoryRequirements.calloc()
        val image = VU.run(memAllocLong(1), "create staging image",
            { vkCreateImage(device, imageInfo, null, this) })

        vkGetImageMemoryRequirements(device, image, reqs)

        val allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)
            .allocationSize(reqs.size())
            .memoryTypeIndex(physicalDevice.getMemoryType(reqs.memoryTypeBits(), memoryFlags).second)

        val memory = VU.run(memAllocLong(1), "allocate image staging memory",
            { vkAllocateMemory(device, allocInfo, null, this) },
            { imageInfo.free(); allocInfo.free(); reqs.free(); extent.free() })

        vkBindImageMemory(device, image, memory, 0)

        return VulkanImage(image, memory, reqs.size())
    }


    fun copyFrom(data: ByteBuffer) {
        if(data.remaining() > stagingImage.maxSize) {
            logger.warn("Allocated image size for $this (${stagingImage.maxSize}) less than copy source size ${data.remaining()}.")
            return
        }

        if (mipLevels == 1) {
            with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
                image = createImage(width, height, depth,
                    format, VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT,
                    VK_IMAGE_TILING_OPTIMAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    mipLevels)

                val dest = memAllocPointer(1)
                vkMapMemory(device, stagingImage.memory, 0, data.remaining() * 1L, 0, dest)
                memCopy(memAddress(data), dest.get(0), data.remaining())
                vkUnmapMemory(device, stagingImage.memory)
                memFree(dest)

                transitionLayout(stagingImage.image,
                    VK_IMAGE_LAYOUT_PREINITIALIZED,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, mipLevels,
                    commandBuffer = this)
                transitionLayout(image!!.image,
                    VK_IMAGE_LAYOUT_PREINITIALIZED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, mipLevels,
                    commandBuffer = this)

                image!!.copyFrom(this, stagingImage)
                transitionLayout(image!!.image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, mipLevels,
                    commandBuffer = this)

                this.endCommandBuffer(device, commandPool, queue, flush = true, dealloc = true)
            }
        } else {
            val buffer = VU.createBuffer(device, memoryProperties, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, false, data.limit() * 1L)

            with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
                image = createImage(width, height, depth,
                    format, VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT or VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                    VK_IMAGE_TILING_OPTIMAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    mipLevels)

                buffer.copyFrom(data)

                transitionLayout(image!!.image, VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, commandBuffer = this)
                image!!.copyFrom(this, buffer)
                transitionLayout(image!!.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, 1, commandBuffer = this)

                this.endCommandBuffer(device, commandPool, queue, flush = true, dealloc = true)
            }

            val imageBlit = VkImageBlit.calloc(1)
            with(VU.newCommandBuffer(device, commandPool, autostart = true)) mipmapCreation@ {

                (1..mipLevels).forEach { mipLevel ->
                    imageBlit.srcSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, mipLevel - 1, 0, 1)
                    imageBlit.srcOffsets(1).set(width shr (mipLevel - 1), height shr (mipLevel - 1), 1)

                    imageBlit.dstSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, mipLevel, 0, 1)
                    imageBlit.dstOffsets(1).set(width shr (mipLevel), height shr (mipLevel), 1)

                    val mipRange = VkImageSubresourceRange.calloc()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseArrayLayer(0)
                        .layerCount(1)
                        .baseMipLevel(mipLevel)

                    transitionLayout(image!!.image, VK_IMAGE_LAYOUT_UNDEFINED,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, subresourceRange = mipRange,
                        srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                        dstStage = VK_PIPELINE_STAGE_HOST_BIT,
                        commandBuffer = this@mipmapCreation)

                    vkCmdBlitImage(this@mipmapCreation,
                        image!!.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        image!!.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        imageBlit, VK_FILTER_LINEAR)

                    transitionLayout(image!!.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, subresourceRange = mipRange,
                        srcStage = VK_PIPELINE_STAGE_HOST_BIT,
                        dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                        commandBuffer = this@mipmapCreation)
                }

                transitionLayout(image!!.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, mipLevels,
                    commandBuffer = this@mipmapCreation)

                this@mipmapCreation.endCommandBuffer(device, commandPool, queue, flush = true, dealloc = true)
            }

            imageBlit.free()
            buffer.close()
        }

        image!!.sampler = createSampler()
        image!!.view = createImageView(image!!, format)

    }

    private fun createImageView(image: VulkanImage, format: Int): Long {
        val subresourceRange = VkImageSubresourceRange.calloc().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1)

        val vi = VkImageViewCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .pNext(NULL)
            .image(image.image)
            .viewType(if (depth > 1) {
                VK_IMAGE_VIEW_TYPE_3D
            } else {
                VK_IMAGE_VIEW_TYPE_2D
            })
            .format(format)
            .subresourceRange(subresourceRange)

        val view = VU.run(memAllocLong(1), "Creating image view",
            { vkCreateImageView(device, vi, null, this) },
            { vi.free(); subresourceRange.free(); })

        return view
    }

    private fun createSampler(): Long {
        val samplerInfo = VkSamplerCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            .pNext(NULL)
            .magFilter(VK_FILTER_LINEAR)
            .minFilter(VK_FILTER_LINEAR)
            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
            .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .mipLodBias(0.0f)
            .anisotropyEnable(true)
            .maxAnisotropy(8.0f)
            .minLod(0.0f)
            .maxLod(mipLevels * 1.0f)
            .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)
            .compareOp(VK_COMPARE_OP_NEVER)

        return VU.run(memAllocLong(1), "creating sampler",
            { vkCreateSampler(device, samplerInfo, null, this) },
            { samplerInfo.free() })
    }

    companion object {
        private val logger = LoggerFactory.getLogger("VulkanTexture")

        private val StandardAlphaColorModel = ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            intArrayOf(8, 8, 8, 8),
            true,
            false,
            ComponentColorModel.TRANSLUCENT,
            DataBuffer.TYPE_BYTE)

        private val StandardColorModel = ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            intArrayOf(8, 8, 8, 0),
            false,
            false,
            ComponentColorModel.OPAQUE,
            DataBuffer.TYPE_BYTE)

        fun loadFromFile(device: VkDevice, physicalDevice: VkPhysicalDevice,
                         memoryProperties: VkPhysicalDeviceMemoryProperties,
                         commandPool: Long, queue: VkQueue,
                         filename: String, linearInterpolation: Boolean,
                         mipmapLevels: Int): VulkanTexture? {
            val stream = FileInputStream(filename)
            val type = filename.substringAfterLast('.')

            logger.info("Loading texture with m=$mipmapLevels from $filename")

            return loadFromFile(device, physicalDevice,
                memoryProperties, commandPool, queue,
                stream, type, linearInterpolation, mipmapLevels)
        }

        fun loadFromFile(device: VkDevice, physicalDevice: VkPhysicalDevice,
                         memoryProperties: VkPhysicalDeviceMemoryProperties,
                         commandPool: Long, queue: VkQueue,
                         stream: InputStream, type: String, linearInterpolation: Boolean,
                         mipmapLevels: Int): VulkanTexture? {
            val bi: BufferedImage
            val flippedImage: BufferedImage
            val imageData: ByteBuffer
            val fis: FileInputStream
            val pixels: IntArray
            val buffer: ByteArray

            if (type.endsWith("tga")) {
                try {
                    val reader = BufferedInputStream(stream)
                    buffer = ByteArray(stream.available())
                    reader.read(buffer)
                    reader.close()

                    pixels = TGAReader.read(buffer, TGAReader.ARGB)
                    val width = TGAReader.getWidth(buffer)
                    val height = TGAReader.getHeight(buffer)
                    bi = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    bi.setRGB(0, 0, width, height, pixels, 0, width)
                } catch (e: Exception) {
                    logger.error("Could not read image from TGA. ${e.message}")
                    return null
                }

            } else {
                try {
                    val reader = BufferedInputStream(stream)
                    bi = ImageIO.read(stream)
                    reader.close()

                } catch (e: Exception) {
                    logger.error("Could not read image: ${e.message}")
                    return null
                }

            }

            // convert to OpenGL UV space
            flippedImage = createFlipped(bi)
            imageData = bufferedImageToRGBABuffer(flippedImage)

            var texWidth = 2
            var texHeight = 2

            while (texWidth < bi.width) {
                texWidth *= 2
            }
            while (texHeight < bi.height) {
                texHeight *= 2
            }

            val tex = VulkanTexture(
                device, physicalDevice, memoryProperties,
                commandPool, queue,
                texWidth, texHeight, 1,
                if (bi.colorModel.hasAlpha()) {
                    VK_FORMAT_R8G8B8A8_SRGB
                } else {
                    VK_FORMAT_R8G8B8A8_SRGB
                }, mipmapLevels)

            tex.copyFrom(imageData)

            return tex
        }

        private fun bufferedImageToRGBABuffer(bufferedImage: BufferedImage): ByteBuffer {
            val imageBuffer: ByteBuffer
            val raster: WritableRaster
            val texImage: BufferedImage

            var texWidth = 2
            var texHeight = 2

            while (texWidth < bufferedImage.width) {
                texWidth *= 2
            }
            while (texHeight < bufferedImage.height) {
                texHeight *= 2
            }

            if (bufferedImage.colorModel.hasAlpha()) {
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, texWidth, texHeight, 4, null)
                texImage = BufferedImage(StandardAlphaColorModel, raster, false, Hashtable<Any, Any>())
            } else {
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, texWidth, texHeight, 3, null)
                texImage = BufferedImage(StandardColorModel, raster, false, Hashtable<Any, Any>())
            }

            val g = texImage.graphics
            g.color = Color(0.0f, 0.0f, 0.0f, 1.0f)
            g.fillRect(0, 0, texWidth, texHeight)
            g.drawImage(bufferedImage, 0, 0, null)
            g.dispose()

            val data = (texImage.raster.dataBuffer as DataBufferByte).data

            imageBuffer = memAlloc(data.size)
            imageBuffer.order(ByteOrder.nativeOrder())
            imageBuffer.put(data, 0, data.size)
            imageBuffer.rewind()

            return imageBuffer
        }

        // the following three routines are from
        // http://stackoverflow.com/a/23458883/2129040,
        // authored by MarcoG
        private fun createFlipped(image: BufferedImage): BufferedImage {
            val at = AffineTransform()
            at.concatenate(AffineTransform.getScaleInstance(1.0, -1.0))
            at.concatenate(AffineTransform.getTranslateInstance(0.0, (-image.height).toDouble()))
            return createTransformed(image, at)
        }

        private fun createRotated(image: BufferedImage): BufferedImage {
            val at = AffineTransform.getRotateInstance(
                Math.PI, (image.width / 2).toDouble(), image.height / 2.0)
            return createTransformed(image, at)
        }

        private fun createTransformed(
            image: BufferedImage, at: AffineTransform): BufferedImage {
            val newImage = BufferedImage(
                image.width, image.height,
                BufferedImage.TYPE_INT_ARGB)
            val g = newImage.createGraphics()
            g.transform(at)
            g.drawImage(image, 0, 0, null)
            g.dispose()
            return newImage
        }

        fun transitionLayout(image: Long, oldLayout: Int, newLayout: Int, mipLevels: Int = 1,
                             subresourceRange: VkImageSubresourceRange? = null,
                             srcStage: Int = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, dstStage: Int = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                             commandBuffer: VkCommandBuffer) {

            with(commandBuffer) {
                val barrier = VkImageMemoryBarrier.calloc(1)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .pNext(NULL)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)

                if (subresourceRange == null) {
                    barrier.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(mipLevels)
                        .baseArrayLayer(0)
                        .layerCount(1)
                } else {
                    barrier.subresourceRange(subresourceRange)
                }

                if (oldLayout == VK_IMAGE_LAYOUT_PREINITIALIZED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                    barrier
                        .srcAccessMask(VK_ACCESS_HOST_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                } else if (oldLayout == VK_IMAGE_LAYOUT_PREINITIALIZED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                    barrier
                        .srcAccessMask(VK_ACCESS_HOST_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                    barrier
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                } else if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                    barrier
                        .srcAccessMask(0)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    barrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                    barrier
                        .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                } else if(oldLayout == KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR && newLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                    barrier
                        .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                } else if(oldLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL && newLayout == KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) {
                    barrier
                        .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                } else {
                    logger.error("Unsupported layout transition: $oldLayout -> $newLayout")
                }

                vkCmdPipelineBarrier(this,
                    srcStage,
                    dstStage,
                    0, null, null, barrier)

                barrier.free()
            }
        }
    }

    override fun close() {
        image?.let {
            if (it.view != -1L) vkDestroyImageView(device, it.view, null)
            if (it.image != -1L) vkDestroyImage(device, it.image, null)
            if (it.sampler != -1L) vkDestroySampler(device, it.sampler, null)
            if (it.memory != -1L) vkFreeMemory(device, it.memory, null)
        }

        if (stagingImage.image != -1L) vkDestroyImage(device, stagingImage.image, null)
        if (stagingImage.memory != -1L) vkFreeMemory(device, stagingImage.memory, null)
    }
}
