package scenery.backends.vulkan

import cleargl.TGAReader
import org.lwjgl.vulkan.VkImageCreateInfo
import java.awt.Color
import java.awt.color.ColorSpace
import java.awt.geom.AffineTransform
import java.awt.image.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.util.*
import javax.imageio.ImageIO
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.NativeResource
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * Created by ulrik on 11/1/2016.
 */
class VulkanTexture(val device: VkDevice, val physicalDevice: VkPhysicalDevice,
                    val commandPool: Long, val queue: VkQueue,
                    val width: Int, val height: Int, val depth: Int = 1,
                    val format: Int = VK_FORMAT_R8G8B8_UNORM) {
    protected var logger: Logger = LoggerFactory.getLogger("VulkanRenderer")

    var image: VulkanImage? = null
    private var stagingImage: VulkanImage

    inner class VulkanImage(var image: Long = -1L, var memory: Long = -1L) {

        var sampler: Long = -1L
        var view: Long = -1L
        var descriptorSet: Long = -1L

        fun transitionLayout(oldLayout: Int, newLayout: Int) {
            with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
                val barrier = VkImageMemoryBarrier.calloc(1)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .pNext(NULL)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this@VulkanImage.image)
                    .subresourceRange(VkImageSubresourceRange.calloc().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1))

                if (oldLayout == VK_IMAGE_LAYOUT_PREINITIALIZED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_HOST_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                } else if (oldLayout == VK_IMAGE_LAYOUT_PREINITIALIZED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_HOST_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                } else {
                    logger.error("Unsupported layout transition: $oldLayout -> $newLayout")
                }

                vkCmdPipelineBarrier(this,
                    VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    0, null, null, barrier)

                this.endCommandBuffer(device, commandPool, queue, flush = true, dealloc = true)
            }
        }

        fun copyFrom(image: VulkanImage) {
            with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
                val subresource = VkImageSubresourceLayers.calloc()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseArrayLayer(0)
                    .mipLevel(0)
                    .layerCount(1)

                val region = VkImageCopy.calloc(1)
                    .srcSubresource(subresource)
                    .dstSubresource(subresource)
                    .srcOffset(VkOffset3D.calloc().set(0, 0, 0))
                    .dstOffset(VkOffset3D.calloc().set(0, 0, 0))
                    .extent(VkExtent3D.calloc().set(width, height, depth))

                vkCmdCopyImage(this,
                    image.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    this@VulkanImage.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region)

                this.endCommandBuffer(device, commandPool, queue, flush = true, dealloc = true)
            }
        }
    }

    init {
        stagingImage = createImage(width, height, depth,
            format, VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
            VK_IMAGE_TILING_LINEAR,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_CACHED_BIT)
    }

    private fun createImage(width: Int, height: Int, depth: Int, format: Int, usage: Int, tiling: Int, memoryFlags: Int): VulkanImage {
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
            .mipLevels(1)
            .arrayLayers(1)
            .format(format)
            .tiling(tiling)
            .initialLayout(VK_IMAGE_LAYOUT_PREINITIALIZED)
            .usage(usage)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .flags(0)

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
            { imageInfo.free(); allocInfo.free(); reqs.free() })

        vkBindImageMemory(device, image, memory, 0)

        return VulkanImage(image, memory)
    }


    fun copyFrom(buffer: ByteBuffer) {
        val dest = memAllocPointer(1)

        vkMapMemory(device, stagingImage.memory, 0, buffer.remaining() * 1L, 0, dest)
        memCopy(memAddress(buffer), dest.get(0), buffer.remaining())
        vkUnmapMemory(device, stagingImage.memory)
        memFree(dest)

        image = createImage(width, height, depth,
            format, VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT,
            VK_IMAGE_TILING_OPTIMAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)

        stagingImage!!.transitionLayout(VK_IMAGE_LAYOUT_PREINITIALIZED, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)

        image!!.transitionLayout(VK_IMAGE_LAYOUT_PREINITIALIZED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
        image!!.copyFrom(stagingImage)
        image!!.transitionLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

        image!!.sampler = createSampler()
        image!!.view = createImageView(image!!, format)
    }

    private fun createDescriptorSet(descriptorSetLayout: Long, descriptorPool: Long, targetBinding: Int = 0, arrayElement: Int = 0): Long {
        logger.info("Creating texture descriptor set with 1 bindings, DSL=$descriptorSetLayout")
        val pDescriptorSetLayout = memAllocLong(1)
        pDescriptorSetLayout.put(0, descriptorSetLayout)

        val allocInfo = VkDescriptorSetAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .pNext(NULL)
            .descriptorPool(descriptorPool)
            .pSetLayouts(pDescriptorSetLayout)

        val descriptorSet = VU.run(memAllocLong(1), "createDescriptorSet",
            { vkAllocateDescriptorSets(device, allocInfo, this) },
            { allocInfo.free(); memFree(pDescriptorSetLayout) })

        val d = VkDescriptorImageInfo.calloc(1)
            .imageView(image!!.view)
            .sampler(image!!.sampler)
            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

        val writeDescriptorSet = VkWriteDescriptorSet.calloc(1)
            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            .pNext(NULL)
            .dstSet(descriptorSet)
            .dstBinding(targetBinding)
            .dstArrayElement(arrayElement)
            .pImageInfo(d as VkDescriptorImageInfo.Buffer)

        vkUpdateDescriptorSets(device, writeDescriptorSet, null)
        writeDescriptorSet.free()
        (d as NativeResource).free()

        image!!.descriptorSet = descriptorSet
        return descriptorSet
    }

    private fun createImageView(image: VulkanImage, format: Int): Long {
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
            .subresourceRange(VkImageSubresourceRange.calloc().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1))

        val view = VU.run(memAllocLong(1), "Creating image view",
            { vkCreateImageView(device, vi, null, this) })

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
            .maxAnisotropy(16.0f)
            .minLod(0.0f)
            .maxLod(1.0f)
            .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)

        return VU.run(memAllocLong(1), "creating sampler",
            { vkCreateSampler(device, samplerInfo, null, this) })
    }

    companion object {

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

        fun loadFromFile(device: VkDevice, physicalDevice: VkPhysicalDevice, commandPool: Long, queue: VkQueue,
                         filename: String, linearInterpolation: Boolean,
                         mipmapLevels: Int): VulkanTexture? {
            val bi: BufferedImage
            val flippedImage: BufferedImage
            val imageData: ByteBuffer
            var fis: FileInputStream? = null
            var channel: FileChannel? = null
            var pixels: IntArray? = null

            if (filename.substring(filename.lastIndexOf('.')).toLowerCase().endsWith("tga")) {
                var buffer: ByteArray? = null

                try {
                    fis = FileInputStream(filename)
                    channel = fis.channel
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    channel!!.transferTo(0, channel.size(), Channels.newChannel(byteArrayOutputStream))
                    buffer = byteArrayOutputStream.toByteArray()

                    channel.close()
                    fis.close()

                    pixels = TGAReader.read(buffer, TGAReader.ARGB)
                    val width = TGAReader.getWidth(buffer)
                    val height = TGAReader.getHeight(buffer)
                    bi = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    bi.setRGB(0, 0, width, height, pixels, 0, width)
                } catch (e: Exception) {
                    System.err.println("GLTexture: could not read image from TGA$filename.")
                    return null
                }

            } else {
                try {
                    fis = FileInputStream(filename)
                    channel = fis.channel
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    channel!!.transferTo(0, channel.size(), Channels.newChannel(byteArrayOutputStream))
                    bi = ImageIO.read(ByteArrayInputStream(byteArrayOutputStream.toByteArray()))

                    channel.close()
                } catch (e: Exception) {
                    System.err.println("GLTexture: could not read image from $filename.")
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
                device, physicalDevice, commandPool, queue,
                texWidth, texHeight, 1,
                if (bi.colorModel.hasAlpha()) {
                    VK_FORMAT_R8G8B8A8_UNORM
                } else {
                    VK_FORMAT_R8G8B8_UNORM
                })

            tex.copyFrom(imageData)

            return tex
        }

        private fun BufferedImage.toVulkanFormat(): Int {
            when (this.data.dataBuffer.dataType) {
                DataBuffer.TYPE_BYTE -> {
                }
                DataBuffer.TYPE_DOUBLE -> {
                }
                DataBuffer.TYPE_INT -> {
                }
                DataBuffer.TYPE_SHORT -> {
                }
            }

            return -1
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

            imageBuffer = ByteBuffer.allocateDirect(data.size)
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
    }
}
