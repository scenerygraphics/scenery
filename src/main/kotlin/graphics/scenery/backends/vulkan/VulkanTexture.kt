package graphics.scenery.backends.vulkan

import cleargl.GLTypeEnum
import cleargl.TGAReader
import graphics.scenery.GenericTexture
import graphics.scenery.backends.vulkan.VU.newCommandBuffer
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import vkn.*
import java.awt.Color
import java.awt.color.ColorSpace
import java.awt.geom.AffineTransform
import java.awt.image.*
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.imageio.ImageIO
import kotlin.streams.toList

/**
 * Vulkan Textures class. Provides static methods to load textures from files.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanTexture(val device: VulkanDevice,
                         val commandPool: VkCommandPool, val queue: VkQueue,
                         val width: Int, val height: Int, val depth: Int = 1,
                         val format: VkFormat = VkFormat.R8G8B8_SRGB, var mipLevels: Int = 1,
                         val minFilterLinear: Boolean = true, val maxFilterLinear: Boolean = true) : AutoCloseable {
    protected val logger by LazyLogger()

    var image: VulkanImage? = null
    private var stagingImage: VulkanImage
    private var gt: GenericTexture? = null

    inner class VulkanImage(var image: VkImage = -1L, var memory: VkDeviceMemory = -1L, val maxSize: VkDeviceSize = -1L) {

        var sampler: VkSampler = NULL
        var view: VkImageView = NULL


        fun copyFrom(commandBuffer: VkCommandBuffer, buffer: VulkanBuffer) {

            val bufferImageCopy = vk.BufferImageCopy(1) {
                imageSubresource.apply {
                    aspectMask = VkImageAspect.COLOR_BIT.i
                    mipLevel = 0
                    baseArrayLayer = 0
                    layerCount = 1
                }
                imageExtent.set(width, height, depth)
//                    imageOffset.set(0, 0, 0) // TODO useless
            }
            commandBuffer.copyBufferToImage(buffer.vulkanBuffer, this@VulkanImage.image, VkImageLayout.TRANSFER_DST_OPTIMAL, bufferImageCopy)
        }

        fun copyFrom(commandBuffer: VkCommandBuffer, image: VulkanImage) {

            val subresource = vk.ImageSubresourceLayers {
                aspectMask = VkImageAspect.COLOR_BIT.i
                baseArrayLayer = 0
                mipLevel = 0
                layerCount = 1
            }
            val region = vk.ImageCopy {
                srcSubresource = subresource
                dstSubresource = subresource
//                srcOffset.set(0, 0, 0) TODO useless
//                dstOffset.set(0, 0, 0)
                extent.set(width, height, depth)
            }
            commandBuffer.copyImage(
                image.image, VkImageLayout.TRANSFER_SRC_OPTIMAL,
                this@VulkanImage.image, VkImageLayout.TRANSFER_DST_OPTIMAL,
                region)
        }
    }

    init {
        stagingImage = if (depth == 1) {
            createImage(width, height, depth,
                format, VkImageUsage.TRANSFER_SRC_BIT.i,
                VkImageTiling.LINEAR,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_CACHED_BIT,
                mipLevels = 1)
        } else {
            createImage(16, 16, 1,
                format, VkImageUsage.TRANSFER_SRC_BIT.i,
                VkImageTiling.LINEAR,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_CACHED_BIT,
                mipLevels = 1)
        }
    }

    @Suppress("USELESS_ELVIS", "UNNECESSARY_SAFE_CALL")
    constructor(device: VulkanDevice,
                commandPool: VkCommandPool, queue: VkQueue,
                genericTexture: GenericTexture, mipLevels: Int = 1,
                minFilterLinear: Boolean = true, maxFilterLinear: Boolean = true) : this(device,
        commandPool,
        queue,
        genericTexture.dimensions.x().toInt(),
        genericTexture.dimensions.y().toInt(),
        genericTexture.dimensions.z()?.toInt() ?: 1,
        genericTexture.toVulkanFormat(),
        mipLevels, minFilterLinear, maxFilterLinear) {
        gt = genericTexture
    }

    fun createImage(width: Int, height: Int, depth: Int,
                    format: VkFormat, usage: VkImageUsageFlags,
                    tiling: VkImageTiling, memoryFlags: VkMemoryPropertyFlags, mipLevels: Int,
                    customAllocator: ((VkMemoryRequirements, VkImage) -> VkDeviceMemory)? = null,
                    imageCreateInfo: VkImageCreateInfo? = null): VulkanImage {

        val imageInfo = imageCreateInfo ?: vk.ImageCreateInfo {
            imageType = when (depth) {
                1 -> VkImageType.`2D`
                else -> VkImageType.`3D`
            }
            this.mipLevels = mipLevels
            arrayLayers = 1
            this.format = format
            this.tiling = tiling
            initialLayout = when (depth) {
                1 -> VkImageLayout.PREINITIALIZED
                else -> VkImageLayout.UNDEFINED
            }
            this.usage = usage
            sharingMode = VkSharingMode.EXCLUSIVE
            samples = VkSampleCount.`1_BIT`
            flags = VkImageCreate.MUTABLE_FORMAT_BIT.i
            extent.set(width, height, depth)
        }

        val image = device.vulkanDevice createImage imageInfo

        val reqs = device.vulkanDevice getImageMemoryRequirements image
        val memorySize = reqs.size

        val memory = customAllocator?.invoke(reqs, image) ?: run {
            val allocInfo = vk.MemoryAllocateInfo {
                allocationSize = memorySize
                memoryTypeIndex(device.getMemoryType(reqs.memoryTypeBits(), memoryFlags)[0]) // TODO BUG
            }
            device.vulkanDevice allocateMemory allocInfo
        }

        device.vulkanDevice.bindImageMemory(image, memory)

        return VulkanImage(image, memory, memorySize)
    }


    fun copyFrom(data: ByteBuffer) {
        if (depth == 1 && data.remaining() > stagingImage.maxSize) {
            logger.warn("Allocated image size for $this (${stagingImage.maxSize}) less than copy source size ${data.remaining()}.")
            return
        }

        if (mipLevels == 1) {
            var buffer: VulkanBuffer? = null
            val cmdBuf = device.vulkanDevice.newCommandBuffer(commandPool, autostart = true)
            if (image == null) {
                image = createImage(width, height, depth,
                    format, VkImageUsage.TRANSFER_DST_BIT or VkImageUsage.SAMPLED_BIT,
                    VkImageTiling.OPTIMAL, VkMemoryProperty.DEVICE_LOCAL_BIT.i,
                    mipLevels)
            }

            if (depth == 1) {

                device.vulkanDevice.mappingMemory(stagingImage.memory, 0, data.remaining() * 1L) { dest ->
                    memCopy(memAddress(data), dest, data.remaining().toLong())
                }

                transitionLayout(stagingImage.image,
                    VkImageLayout.PREINITIALIZED,
                    VkImageLayout.TRANSFER_SRC_OPTIMAL, mipLevels,
                    srcStage = VkPipelineStage.HOST_BIT.i,
                    dstStage = VkPipelineStage.TRANSFER_BIT.i,
                    commandBuffer = cmdBuf)
                transitionLayout(image!!.image,
                    VkImageLayout.PREINITIALIZED,
                    VkImageLayout.TRANSFER_DST_OPTIMAL, mipLevels,
                    srcStage = VkPipelineStage.HOST_BIT.i,
                    dstStage = VkPipelineStage.TRANSFER_BIT.i,
                    commandBuffer = cmdBuf)

                image!!.copyFrom(cmdBuf, stagingImage)

                transitionLayout(image!!.image,
                    VkImageLayout.TRANSFER_DST_OPTIMAL,
                    VkImageLayout.SHADER_READ_ONLY_OPTIMAL, mipLevels,
                    srcStage = VkPipelineStage.TRANSFER_BIT.i,
                    dstStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                    commandBuffer = cmdBuf)

            } else {

                buffer = VulkanBuffer(device,
                    data.capacity().toLong(),
                    VkBufferUsage.TRANSFER_SRC_BIT.i,
                    VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                    wantAligned = false)

                buffer.copyFrom(data)

                transitionLayout(image!!.image,
                    VkImageLayout.UNDEFINED,
                    VkImageLayout.TRANSFER_DST_OPTIMAL, mipLevels,
                    srcStage = VkPipelineStage.HOST_BIT.i,
                    dstStage = VkPipelineStage.TRANSFER_BIT.i,
                    commandBuffer = cmdBuf)

                image!!.copyFrom(cmdBuf, buffer)

                transitionLayout(image!!.image,
                    VkImageLayout.TRANSFER_DST_OPTIMAL,
                    VkImageLayout.SHADER_READ_ONLY_OPTIMAL, mipLevels,
                    srcStage = VkPipelineStage.TRANSFER_BIT.i,
                    dstStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                    commandBuffer = cmdBuf)
            }

            cmdBuf.end(device, commandPool, queue, flush = true, dealloc = true)
            buffer?.close()

        } else {

            val buffer = VulkanBuffer(device,
                data.limit().toLong(),
                VkBufferUsage.TRANSFER_SRC_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT.i,
                wantAligned = false)

            val cmdBuff = VU.newCommandBuffer(device, commandPool, autostart = true)
            if (image == null) {
                image = createImage(width, height, depth,
                    format, VkImageUsage.TRANSFER_DST_BIT or VkImageUsage.SAMPLED_BIT or VkImageUsage.TRANSFER_SRC_BIT,
                    VkImageTiling.OPTIMAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    mipLevels)
            }

            buffer.copyFrom(data)

            transitionLayout(image!!.image, VkImageLayout.UNDEFINED,
                VkImageLayout.TRANSFER_DST_OPTIMAL, 1, commandBuffer = cmdBuff,
                srcStage = VkPipelineStage.HOST_BIT.i,
                dstStage = VkPipelineStage.TRANSFER_BIT.i)
            image!!.copyFrom(cmdBuff, buffer)
            transitionLayout(image!!.image, VkImageLayout.TRANSFER_DST_OPTIMAL,
                VkImageLayout.TRANSFER_SRC_OPTIMAL, 1, commandBuffer = cmdBuff,
                srcStage = VkPipelineStage.TRANSFER_BIT.i,
                dstStage = VkPipelineStage.TRANSFER_BIT.i)

            cmdBuff.end(device, commandPool, queue, flush = true, dealloc = true)

            val imageBlit = VkImageBlit.calloc(1)
            with(VU.newCommandBuffer(device, commandPool, autostart = true)) mipmapCreation@{

                (1..mipLevels).forEach { mipLevel ->
                    imageBlit.srcSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, mipLevel - 1, 0, 1)
                    imageBlit.srcOffsets(1).set(width shr (mipLevel - 1), height shr (mipLevel - 1), 1)

                    val dstWidth = width shr mipLevel
                    val dstHeight = height shr mipLevel

                    if (dstWidth < 2 || dstHeight < 2) {
                        return@forEach
                    }

                    imageBlit.dstSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, mipLevel, 0, 1)
                    imageBlit.dstOffsets(1).set(width shr (mipLevel), height shr (mipLevel), 1)

                    val mipSourceRange = VkImageSubresourceRange.calloc()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseArrayLayer(0)
                        .layerCount(1)
                        .baseMipLevel(mipLevel - 1)
                        .levelCount(1)

                    val mipTargetRange = VkImageSubresourceRange.calloc()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseArrayLayer(0)
                        .layerCount(1)
                        .baseMipLevel(mipLevel)
                        .levelCount(1)

                    if (mipLevel > 1) {
                        transitionLayout(image!!.image,
                            VkImageLayout.SHADER_READ_ONLY_OPTIMAL,
                            VkImageLayout.TRANSFER_SRC_OPTIMAL,
                            subresourceRange = mipSourceRange,
                            srcStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                            dstStage = VkPipelineStage.TRANSFER_BIT.i,
                            commandBuffer = this@mipmapCreation)
                    }

                    transitionLayout(image!!.image,
                        VkImageLayout.UNDEFINED,
                        VkImageLayout.TRANSFER_DST_OPTIMAL,
                        subresourceRange = mipTargetRange,
                        srcStage = VkPipelineStage.HOST_BIT.i,
                        dstStage = VkPipelineStage.TRANSFER_BIT.i,
                        commandBuffer = this@mipmapCreation)

                    vkCmdBlitImage(this@mipmapCreation,
                        image!!.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        image!!.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        imageBlit, VK_FILTER_LINEAR)

                    transitionLayout(image!!.image,
                        VkImageLayout.TRANSFER_SRC_OPTIMAL,
                        VkImageLayout.SHADER_READ_ONLY_OPTIMAL, subresourceRange = mipSourceRange,
                        srcStage = VkPipelineStage.TRANSFER_BIT.i,
                        dstStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                        commandBuffer = this@mipmapCreation)

                    transitionLayout(image!!.image,
                        VkImageLayout.TRANSFER_DST_OPTIMAL,
                        VkImageLayout.SHADER_READ_ONLY_OPTIMAL, subresourceRange = mipTargetRange,
                        srcStage = VkPipelineStage.TRANSFER_BIT.i,
                        dstStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                        commandBuffer = this@mipmapCreation)

                    mipSourceRange.free()
                    mipTargetRange.free()
                }

                this@mipmapCreation.end(device, commandPool, queue, flush = true, dealloc = true)
            }

            imageBlit.free()
            buffer.close()
        }

        if (image!!.sampler == NULL)
            image!!.sampler = createSampler()
        if (image!!.view == NULL)
            image!!.view = createImageView(image!!, format)
    }

    fun createImageView(image: VulkanImage, format: VkFormat): Long {
        val subresourceRange = VkImageSubresourceRange.calloc().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, mipLevels, 0, 1)

        var viewFormat = format.i

        gt?.let { genericTexture ->
            if (!genericTexture.normalized && genericTexture.type != GLTypeEnum.Float) {
                logger.info("Shifting format to unsigned int")
                viewFormat += 4
            }
        }

        val vi = VkImageViewCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .pNext(NULL)
            .image(image.image)
            .viewType(if (depth > 1) {
                VK_IMAGE_VIEW_TYPE_3D
            } else {
                VK_IMAGE_VIEW_TYPE_2D
            })
            .format(viewFormat)
            .subresourceRange(subresourceRange)

        if (depth > 1) {
            vi.components(VkComponentMapping.calloc().set(VK_COMPONENT_SWIZZLE_R, VK_COMPONENT_SWIZZLE_R, VK_COMPONENT_SWIZZLE_R, VK_COMPONENT_SWIZZLE_R))
        }

        return VU.getLong("Creating image view",
            { vkCreateImageView(device.vulkanDevice, vi, null, this) },
            { vi.free(); subresourceRange.free(); })
    }

    private fun createSampler(): Long {
        val samplerInfo = VkSamplerCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            .pNext(NULL)
            .magFilter(if (minFilterLinear && !(depth > 1)) {
                VK_FILTER_LINEAR
            } else {
                VK_FILTER_LINEAR
            })
            .minFilter(if (maxFilterLinear && !(depth > 1)) {
                VK_FILTER_LINEAR
            } else {
                VK_FILTER_LINEAR
            })
            .mipmapMode(if (depth == 1) {
                VK_SAMPLER_MIPMAP_MODE_LINEAR
            } else {
                VK_SAMPLER_MIPMAP_MODE_NEAREST
            })
            .addressModeU(if (depth == 1) {
                VK_SAMPLER_ADDRESS_MODE_REPEAT
            } else {
                VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
            })
            .addressModeV(if (depth == 1) {
                VK_SAMPLER_ADDRESS_MODE_REPEAT
            } else {
                VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
            })
            .addressModeW(if (depth == 1) {
                VK_SAMPLER_ADDRESS_MODE_REPEAT
            } else {
                VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
            })
            .mipLodBias(0.0f)
            .anisotropyEnable(depth == 1)
            .maxAnisotropy(if (depth == 1) {
                8.0f
            } else {
                1.0f
            })
            .minLod(0.0f)
            .maxLod(if (depth == 1) {
                mipLevels * 1.0f
            } else {
                0.0f
            })
            .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)
            .compareOp(VK_COMPARE_OP_NEVER)

        return VU.getLong("creating sampler",
            { vkCreateSampler(device.vulkanDevice, samplerInfo, null, this) },
            { samplerInfo.free() })
    }


    companion object {
        private val logger by LazyLogger()

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

        fun loadFromFile(device: VulkanDevice,
                         commandPool: VkCommandPool, queue: VkQueue,
                         filename: String,
                         linearMin: Boolean, linearMax: Boolean,
                         generateMipmaps: Boolean = true): VulkanTexture? {
            val stream = FileInputStream(filename)
            val type = filename.substringAfterLast('.')


            logger.debug("Loading${if (generateMipmaps) {
                " mipmapped"
            } else {
                ""
            }} texture from $filename")

            if (type == "raw") {
                val path = Paths.get(filename)
                val infoFile = path.resolveSibling(path.fileName.toString().substringBeforeLast(".") + ".info")
                val dimensions = Files.lines(infoFile).toList().first().split(",").map { it.toLong() }.toLongArray()

                return loadFromFileRaw(device,
                    commandPool, queue,
                    stream, type, dimensions)
            } else {
                return loadFromFile(device,
                    commandPool, queue,
                    stream, type, linearMin, linearMax, generateMipmaps)
            }
        }

        fun loadFromFile(device: VulkanDevice,
                         commandPool: VkCommandPool, queue: VkQueue,
                         stream: InputStream, type: String,
                         linearMin: Boolean, linearMax: Boolean,
                         generateMipmaps: Boolean = true): VulkanTexture? {
            val bi: BufferedImage
            val flippedImage: BufferedImage
            val imageData: ByteBuffer
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

            stream.close()

            // convert to OpenGL UV space
            flippedImage = createFlipped(bi)
            imageData = bufferedImageToRGBABuffer(flippedImage)

            var texWidth = 2
            var texHeight = 2
            var levelsW = 1
            var levelsH = 1

            while (texWidth < bi.width) {
                texWidth *= 2
                levelsW++
            }
            while (texHeight < bi.height) {
                texHeight *= 2
                levelsH++
            }

            val mipmapLevels = if (generateMipmaps) {
                Math.max(levelsW, levelsH)
            } else {
                1
            }

            val tex = VulkanTexture(
                device,
                commandPool, queue,
                texWidth, texHeight, 1,
                if (bi.colorModel.hasAlpha()) {
                    VkFormat.R8G8B8A8_SRGB
                } else {
                    VkFormat.R8G8B8A8_SRGB
                }, mipmapLevels, linearMin, linearMax)

            tex.copyFrom(imageData)
            memFree(imageData)

            return tex
        }

        @Suppress("UNUSED_PARAMETER")
        fun loadFromFileRaw(device: VulkanDevice,
                            commandPool: Long, queue: VkQueue,
                            stream: InputStream, type: String, dimensions: LongArray): VulkanTexture? {
            val imageData: ByteBuffer = ByteBuffer.allocateDirect((2 * dimensions[0] * dimensions[1] * dimensions[2]).toInt())
            val buffer = ByteArray(1024 * 1024)

            var bytesRead = stream.read(buffer)
            while (bytesRead > -1) {
                imageData.put(buffer)
                bytesRead = stream.read(buffer)
            }

            val tex = VulkanTexture(
                device,
                commandPool, queue,
                dimensions[0].toInt(), dimensions[1].toInt(), dimensions[2].toInt(),
                VkFormat.R16_UINT, 1, true, true)

            tex.copyFrom(imageData)

            stream.close()

            return tex
        }

        fun bufferedImageToRGBABuffer(bufferedImage: BufferedImage): ByteBuffer {
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

        fun transitionLayout(image: VkImage, oldLayout: VkImageLayout, newLayout: VkImageLayout, mipLevels: Int = 1,
                             subresourceRange: VkImageSubresourceRange? = null,
                             srcStage: VkPipelineStageFlags = VkPipelineStage.TOP_OF_PIPE_BIT.i,
                             dstStage: VkPipelineStageFlags = VkPipelineStage.TOP_OF_PIPE_BIT.i,
                             commandBuffer: VkCommandBuffer) {

            val barrier = vk.ImageMemoryBarrier {
                this.oldLayout = oldLayout
                this.newLayout = newLayout
                srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED
                dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED
                this.image = image

                if (subresourceRange == null)
                    this.subresourceRange.apply {
                        aspectMask = VkImageAspect.COLOR_BIT.i
                        baseMipLevel = 0
                        levelCount = mipLevels
                        baseArrayLayer = 0
                        layerCount = 1
                    } else {
                    this.subresourceRange = subresourceRange
                }

                if (oldLayout == VkImageLayout.PREINITIALIZED && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    srcAccessMask = VkAccess.HOST_WRITE_BIT.i
                    dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                } else if (oldLayout == VkImageLayout.PREINITIALIZED && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL) {
                    srcAccessMask = VkAccess.HOST_WRITE_BIT.i
                    dstAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                } else if (oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL) {
                    srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                    dstAccessMask = VkAccess.SHADER_READ_BIT.i
                } else if (oldLayout == VkImageLayout.UNDEFINED && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL) {
                    srcAccessMask = 0
                    dstAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                } else if (oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                    dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL) {
                    srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    dstAccessMask = VkAccess.SHADER_READ_BIT.i
                } else if (oldLayout == VkImageLayout.PRESENT_SRC_KHR && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    srcAccessMask = VkAccess.MEMORY_READ_BIT.i
                    dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.PRESENT_SRC_KHR) {
                    srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    dstAccessMask = VkAccess.MEMORY_READ_BIT.i
                } else if (oldLayout == VkImageLayout.UNDEFINED && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL) {
                    srcAccessMask = 0
                    dstAccessMask = VkAccess.SHADER_READ_BIT.i
                } else if (oldLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    srcAccessMask = VkAccess.SHADER_READ_BIT.i
                    dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL) {
                    srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    dstAccessMask = VkAccess.SHADER_READ_BIT.i
                } else if (oldLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL) {
                    srcAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                    dstAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                } else if (oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL) {
                    srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                    dstAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                } else if (oldLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    srcAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                    dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL) {
                    srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    dstAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                } else if (oldLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL) {
                    srcAccessMask = VkAccess.INPUT_ATTACHMENT_READ_BIT.i
                    dstAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                } else if (oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                    srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                    dstAccessMask = VkAccess.DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.i
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                    srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    dstAccessMask = VkAccess.DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.i
                } else if (oldLayout == VkImageLayout.PRESENT_SRC_KHR && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    srcAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                    dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.PRESENT_SRC_KHR) {
                    srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    dstAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                } else if (oldLayout == VkImageLayout.UNDEFINED && newLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL) {
                    srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                    dstAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                } else {
                    logger.error("Unsupported layout transition: $oldLayout -> $newLayout")
                }
            }
            logger.trace("Transition: {} -> {} with srcAccessMark={}, dstAccessMask={}, srcStage={}, dstStage={}", oldLayout, newLayout, barrier.srcAccessMask(), barrier.dstAccessMask(), srcStage, dstStage)

            commandBuffer.pipelineBarrier(
                srcStage,
                dstStage,
                0, null, null, barrier)
        }

        private fun GenericTexture.toVulkanFormat(): VkFormat {
            return when (this.type) {
                GLTypeEnum.Byte -> when (this.channels) {
                    1 -> VkFormat.R8_SNORM
                    2 -> VkFormat.R8G8_SNORM
                    3 -> VkFormat.R8G8B8_SNORM
                    4 -> VkFormat.R8G8B8A8_SNORM

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VkFormat.R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.UnsignedByte -> when (this.channels) {
                    1 -> VkFormat.R8_UNORM
                    2 -> VkFormat.R8G8_UNORM
                    3 -> VkFormat.R8G8B8_UNORM
                    4 -> VkFormat.R8G8B8A8_UNORM

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VkFormat.R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.Short -> when (this.channels) {
                    1 -> VkFormat.R16_SNORM
                    2 -> VkFormat.R16G16_SNORM
                    3 -> VkFormat.R16G16B16_SNORM
                    4 -> VkFormat.R16G16B16A16_SNORM

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VkFormat.R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.UnsignedShort -> when (this.channels) {
                    1 -> VkFormat.R16_UNORM
                    2 -> VkFormat.R16G16_UNORM
                    3 -> VkFormat.R16G16B16_UNORM
                    4 -> VkFormat.R16G16B16A16_UNORM

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VkFormat.R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.Int -> when (this.channels) {
                    1 -> VkFormat.R32_SINT
                    2 -> VkFormat.R32G32_SINT
                    3 -> VkFormat.R32G32B32_SINT
                    4 -> VkFormat.R32G32B32A32_SINT

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VkFormat.R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.UnsignedInt -> when (this.channels) {
                    1 -> VkFormat.R32_UINT
                    2 -> VkFormat.R32G32_UINT
                    3 -> VkFormat.R32G32B32_UINT
                    4 -> VkFormat.R32G32B32A32_UINT

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VkFormat.R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.Float -> when (this.channels) {
                    1 -> VkFormat.R32_SFLOAT
                    2 -> VkFormat.R32G32_SFLOAT
                    3 -> VkFormat.R32G32B32_SFLOAT
                    4 -> VkFormat.R32G32B32A32_SFLOAT

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VkFormat.R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.Double -> TODO("Double format textures are not supported")
            }
        }
    }

    override fun close() {
        image?.let {
            if (it.view != NULL) {
                vkDestroyImageView(device.vulkanDevice, it.view, null)
                it.view = NULL
            }

            if (it.image != -1L) {
                vkDestroyImage(device.vulkanDevice, it.image, null)
                it.image = -1L
            }

            if (it.sampler != NULL) {
                vkDestroySampler(device.vulkanDevice, it.sampler, null)
                it.sampler = NULL
            }

            if (it.memory != -1L) {
                vkFreeMemory(device.vulkanDevice, it.memory, null)
                it.memory = -1L
            }
        }

        if (stagingImage.image != -1L) {
            vkDestroyImage(device.vulkanDevice, stagingImage.image, null)
            stagingImage.image = -1L
        }

        if (stagingImage.memory != -1L) {
            vkFreeMemory(device.vulkanDevice, stagingImage.memory, null)
            stagingImage.memory = -1L
        }
    }


}
