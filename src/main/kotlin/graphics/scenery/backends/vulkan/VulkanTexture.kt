package graphics.scenery.backends.vulkan

import cleargl.GLTypeEnum
import cleargl.TGAReader
import glm_.L
import graphics.scenery.GenericTexture
import graphics.scenery.utils.LazyLogger
import kool.free
import kool.rem
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkImageCreateInfo
import vkk.*
import vkk.`object`.VkDeviceMemory
import vkk.`object`.VkDeviceSize
import vkk.`object`.VkImage
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
 * Vulkan Texture class. Creates a texture on the [device], with [width]x[height]x[depth],
 * of [format], with a given number of [mipLevels]. Filtering can be set via
 * [minFilterLinear] and [maxFilterLinear]. Needs to be supplied with a [queue] to execute
 * generic operations on, and a [transferQueue] for transfer operations. Both are allowed to
 * be the same.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanTexture(val device: VulkanDevice,
                         val commandPools: VulkanRenderer.CommandPools, val queue: VkQueue, val transferQueue: VkQueue,
                         val width: Int, val height: Int, val depth: Int = 1,
                         val format: Int = VK_FORMAT_R8G8B8_SRGB, var mipLevels: Int = 1,
                         val minFilterLinear: Boolean = true, val maxFilterLinear: Boolean = true) : AutoCloseable {
    protected val logger by LazyLogger()

    /** The Vulkan image associated with this texture. */
    var image: VulkanImage
        protected set

    private var stagingImage: VulkanImage
    private var gt: GenericTexture? = null

    val vkDev get() = device.vulkanDevice

    /**
     * Wrapper class for holding on to raw Vulkan [image]s backed by [memory].
     */
    inner class VulkanImage(var image: VkImage = VkImage(NULL), var memory: Long = -1L, val maxSize: Long = -1L) {

        /** Raw Vulkan sampler. */
        var sampler: Long = -1L
            internal set
        /** Raw Vulkan view. */
        var view: Long = -1L
            internal set

        /**
         * Copies the content of the image from [buffer]. This gets executed
         * within a given [commandBuffer].
         */
        fun copyFrom(commandBuffer: VkCommandBuffer, buffer: VulkanBuffer) {
            with(commandBuffer) {
                val bufferImageCopy = VkBufferImageCopy.calloc(1)

                bufferImageCopy.imageSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)

                bufferImageCopy.imageExtent().set(width, height, depth)
                bufferImageCopy.imageOffset().set(0, 0, 0)

                copyBufferToImage(buffer.vulkanBuffer, this@VulkanImage.image, VkImageLayout.TRANSFER_DST_OPTIMAL, bufferImageCopy)

                bufferImageCopy.free()
            }
        }

        /**
         * Copies the content of the image from a given [VulkanImage], [image].
         * This gets executed within a given [commandBuffer].
         */
        fun copyFrom(commandBuffer: VkCommandBuffer, image: VulkanImage) {
            with(commandBuffer) {
                val subresource = VkImageSubresourceLayers.calloc()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseArrayLayer(0)
                    .mipLevel(0)
                    .layerCount(1)

                val region = vk.ImageCopy {
                    srcSubresource = subresource
                    dstSubresource = subresource
                }
                region.srcOffset().set(0, 0, 0)
                region.dstOffset().set(0, 0, 0)
                region.extent().set(width, height, depth)

                copyImage(image.image, VkImageLayout.TRANSFER_SRC_OPTIMAL, this@VulkanImage.image, VkImageLayout.TRANSFER_DST_OPTIMAL, region)

                subresource.free()
            }
        }
    }

    init {
        stagingImage = if (depth == 1) {
            createImage(width, height, depth,
                format, VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                VK_IMAGE_TILING_LINEAR,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_CACHED_BIT,
                mipLevels = 1)
        } else {
            createImage(16, 16, 1,
                format, VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                VK_IMAGE_TILING_LINEAR,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_CACHED_BIT,
                mipLevels = 1)
        }

        image = createImage(width, height, depth,
            format, VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT or VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
            VK_IMAGE_TILING_OPTIMAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            mipLevels)
    }

    /**
     * Alternative constructor to create a [VulkanTexture] from a [GenericTexture].
     */
    @Suppress("USELESS_ELVIS", "UNNECESSARY_SAFE_CALL")
    constructor(device: VulkanDevice,
                commandPools: VulkanRenderer.CommandPools, queue: VkQueue, transferQueue: VkQueue,
                genericTexture: GenericTexture, mipLevels: Int = 1,
                minFilterLinear: Boolean = true, maxFilterLinear: Boolean = true) : this(device,
        commandPools,
        queue,
        transferQueue,
        genericTexture.dimensions.x().toInt(),
        genericTexture.dimensions.y().toInt(),
        genericTexture.dimensions.z()?.toInt() ?: 1,
        genericTexture.toVulkanFormat(),
        mipLevels, minFilterLinear, maxFilterLinear) {
        gt = genericTexture
    }

    /**
     * Creates a Vulkan image of [format] with a given [width], [height], and [depth].
     * [usage] and [memoryFlags] need to be given, as well as the [tiling] parameter and number of [mipLevels].
     * A custom memory allocator may be used and given as [customAllocator].
     */
    fun createImage(width: Int, height: Int, depth: Int, format: Int,
                    usage: Int, tiling: Int, memoryFlags: Int, mipLevels: Int,
                    customAllocator: ((VkMemoryRequirements, Long) -> Long)? = null, imageCreateInfo: VkImageCreateInfo? = null): VulkanImage {
        val imageInfo = if (imageCreateInfo != null) {
            imageCreateInfo
        } else {
            val i = VkImageCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(if (depth == 1) {
                    VK_IMAGE_TYPE_2D
                } else {
                    VK_IMAGE_TYPE_3D
                })
                .mipLevels(mipLevels)
                .arrayLayers(1)
                .format(format)
                .tiling(tiling)
                .initialLayout(if (depth == 1) {
                    VK_IMAGE_LAYOUT_PREINITIALIZED
                } else {
                    VK_IMAGE_LAYOUT_UNDEFINED
                })
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .flags(VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT)

            i.extent().set(width, height, depth)
            i
        }

        val image = VU.getLong("create staging image",
            { vkCreateImage(device.vulkanDevice, imageInfo, null, this) }, {})

        val reqs = VkMemoryRequirements.calloc()
        vkGetImageMemoryRequirements(device.vulkanDevice, image, reqs)
        val memorySize = reqs.size()

        val memory = if (customAllocator == null) {
            val allocInfo = VkMemoryAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .pNext(NULL)
                .allocationSize(memorySize)
                .memoryTypeIndex(device.getMemoryType(reqs.memoryTypeBits(), memoryFlags).first())

            VU.getLong("allocate image staging memory of size $memorySize",
                { vkAllocateMemory(device.vulkanDevice, allocInfo, null, this) },
                { imageInfo.free(); allocInfo.free() })
        } else {
            customAllocator.invoke(reqs, image)
        }

        reqs.free()

        vkBindImageMemory(device.vulkanDevice, image, memory, 0)

        return VulkanImage(VkImage(image), memory, memorySize)
    }


    /**
     * Copies the data for this texture from a [ByteBuffer], [data].
     */
    fun copyFrom(data: ByteBuffer): VulkanTexture {
        if (image.sampler == -1L) {
            image.sampler = createSampler()
        }

        if (image.view == -1L) {
            image.view = createImageView(image, format)
        }

        if (depth == 1 && data.remaining() > stagingImage.maxSize) {
            logger.warn("Allocated image size for $this (${stagingImage.maxSize}) less than copy source size ${data.remaining()}.")
            return this
        }

        var deallocate = false
        var sourceBuffer = data

        gt?.let { gt ->
            if (gt.channels == 3) {
                logger.debug("Loading RGB texture, padding channels to 4 to fit RGBA")
                val pixelByteSize = when (gt.type) {
                    GLTypeEnum.Byte -> 1
                    GLTypeEnum.UnsignedByte -> 1
                    GLTypeEnum.Short -> 2
                    GLTypeEnum.UnsignedShort -> 2
                    GLTypeEnum.Int -> 4
                    GLTypeEnum.UnsignedInt -> 4
                    GLTypeEnum.Float -> 4
                    GLTypeEnum.Double -> 8
                }

                val storage = memAlloc(data.remaining() / 3 * 4)
                val view = data.duplicate()
                val tmp = ByteArray(pixelByteSize * 3)
                val alpha = (0 until pixelByteSize).map { 255.toByte() }.toByteArray()

                // pad buffer to 4 channels
                while (view.hasRemaining()) {
                    view.get(tmp, 0, 3)
                    storage.put(tmp)
                    storage.put(alpha)
                }

                storage.flip()
                deallocate = true
                sourceBuffer = storage
            } else {
                deallocate = false
                sourceBuffer = data
            }
        }

        val sourceBufferSize = VkDeviceSize(sourceBuffer.rem.L)
        if (mipLevels == 1) {
            vkDev.newCommandBuffer(commandPools.Standard)
                .record {
                    if (depth == 1) {
                        device.mappedMemory(VkDeviceMemory(stagingImage.memory), VkDeviceSize(0), sourceBufferSize) { dest ->
                            sourceBuffer copyTo dest
                        }
                        transitionLayout(stagingImage.image,
                            VkImageLayout.PREINITIALIZED,
                            VkImageLayout.TRANSFER_SRC_OPTIMAL, mipLevels,
                            srcStage = VK_PIPELINE_STAGE_HOST_BIT,
                            dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                            commandBuffer = this)
                        transitionLayout(image.image,
                            VkImageLayout.PREINITIALIZED,
                            VkImageLayout.TRANSFER_DST_OPTIMAL, mipLevels,
                            srcStage = VK_PIPELINE_STAGE_HOST_BIT,
                            dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                            commandBuffer = this)

                        image.copyFrom(this, stagingImage)

                        transitionLayout(image.image,
                            VkImageLayout.TRANSFER_DST_OPTIMAL,
                            VkImageLayout.SHADER_READ_ONLY_OPTIMAL, mipLevels,
                            srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                            dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                            commandBuffer = this)
                    } else {
                        VulkanBuffer(this@VulkanTexture.device,
                            sourceBufferSize,
                            VkBufferUsage.TRANSFER_SRC_BIT.i,
                            VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                            wantAligned = false).use { buf ->

                            buf.copyFrom(sourceBuffer)

                            transitionLayout(image.image,
                                VkImageLayout.UNDEFINED,
                                VkImageLayout.TRANSFER_DST_OPTIMAL, mipLevels,
                                srcStage = VK_PIPELINE_STAGE_HOST_BIT,
                                dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                                commandBuffer = this)

                            image.copyFrom(this, buf)

                            transitionLayout(image.image,
                                VkImageLayout.TRANSFER_DST_OPTIMAL,
                                VkImageLayout.SHADER_READ_ONLY_OPTIMAL, mipLevels,
                                srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                                dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                                commandBuffer = this)
                        }
                    }
                }
                .submit(transferQueue, block = true).deallocate()
        } else {
            val buffer = VulkanBuffer(device,
                sourceBufferSize,
                VkBufferUsage.TRANSFER_SRC_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT.i,
                wantAligned = false)

            vkDev.newCommandBuffer(commandPools.Standard)
                .record {

                    buffer.copyFrom(sourceBuffer)

                    transitionLayout(image.image, VkImageLayout.UNDEFINED,
                        VkImageLayout.TRANSFER_DST_OPTIMAL, 1, commandBuffer = this,
                        srcStage = VK_PIPELINE_STAGE_HOST_BIT,
                        dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT)
                    image.copyFrom(this, buffer)
                    transitionLayout(image.image, VkImageLayout.TRANSFER_DST_OPTIMAL,
                        VkImageLayout.TRANSFER_SRC_OPTIMAL, 1, commandBuffer = this,
                        srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                        dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT)
                }
                .submit(transferQueue, block = true)
                .deallocate()

            vkDev.newCommandBuffer(commandPools.Standard)
                .record {

                    val imageBlit = vk.ImageBlit()
                    for (mipLevel in 1..mipLevels) {
                        imageBlit.srcSubresource.set(VkImageAspect.COLOR_BIT.i, mipLevel - 1, 0, 1)
                        imageBlit.srcOffsets(1).set(width shr (mipLevel - 1), height shr (mipLevel - 1), 1)

                        val dstWidth = width shr mipLevel
                        val dstHeight = height shr mipLevel

                        if (dstWidth < 2 || dstHeight < 2) {
                            break
                        }

                        imageBlit.dstSubresource.set(VkImageAspect.COLOR_BIT.i, mipLevel, 0, 1)
                        imageBlit.dstOffsets(1).set(width shr mipLevel, height shr mipLevel, 1)

                        val mipSourceRange = vk.ImageSubresourceRange {
                            aspectMask = VkImageAspect.COLOR_BIT.i
                            baseArrayLayer = 0
                            layerCount = 1
                            baseMipLevel = mipLevel - 1
                            levelCount = 1
                        }
                        val mipTargetRange = vk.ImageSubresourceRange {
                            aspectMask = VkImageAspect.COLOR_BIT.i
                            baseArrayLayer = 0
                            layerCount = 1
                            baseMipLevel = mipLevel
                            levelCount = 1
                        }
                        if (mipLevel > 1) {
                            transitionLayout(image.image,
                                VkImageLayout.SHADER_READ_ONLY_OPTIMAL,
                                VkImageLayout.TRANSFER_SRC_OPTIMAL,
                                subresourceRange = mipSourceRange,
                                srcStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                                dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                                commandBuffer = this)
                        }

                        transitionLayout(image.image,
                            VkImageLayout.UNDEFINED,
                            VkImageLayout.TRANSFER_DST_OPTIMAL,
                            subresourceRange = mipTargetRange,
                            srcStage = VK_PIPELINE_STAGE_HOST_BIT,
                            dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                            commandBuffer = this)

                        blitImage(
                            image.image, VkImageLayout.TRANSFER_SRC_OPTIMAL,
                            image.image, VkImageLayout.TRANSFER_DST_OPTIMAL,
                            imageBlit, VkFilter.LINEAR)

                        transitionLayout(image.image,
                            VkImageLayout.TRANSFER_SRC_OPTIMAL,
                            VkImageLayout.SHADER_READ_ONLY_OPTIMAL, subresourceRange = mipSourceRange,
                            srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                            dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                            commandBuffer = this)

                        transitionLayout(image.image,
                            VkImageLayout.TRANSFER_DST_OPTIMAL,
                            VkImageLayout.SHADER_READ_ONLY_OPTIMAL, subresourceRange = mipTargetRange,
                            srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT,
                            dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                            commandBuffer = this)
                    }
                }
                .submit(queue).deallocate()

            buffer.close()
        }

        // deallocate in case we moved pixels around
        if (deallocate) {
            sourceBuffer.free()
        }
        return this
    }

    /**
     * Creates a Vulkan image view with [format] for an [image].
     */
    fun createImageView(image: VulkanImage, format: Int): Long {
        val subresourceRange = VkImageSubresourceRange.calloc().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, mipLevels, 0, 1)

        var viewFormat = format

        gt?.let { genericTexture ->
            if (!genericTexture.normalized && genericTexture.type != GLTypeEnum.Float) {
                logger.info("Shifting format to unsigned int")
                viewFormat += 4
            }
        }

        val vi = VkImageViewCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .pNext(NULL)
            .image(image.image.L)
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

    /**
     * Creates a default sampler for this texture.
     */
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


    /**
     * Utility methods for [VulkanTexture].
     */
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

        /**
         * Loads a texture from a file given by [filename], and allocates the [VulkanTexture] on [device].
         */
        fun loadFromFile(device: VulkanDevice,
                         commandPools: VulkanRenderer.CommandPools, queue: VkQueue, transferQueue: VkQueue,
                         filename: String,
                         linearMin: Boolean, linearMax: Boolean,
                         generateMipmaps: Boolean = true): VulkanTexture {
            val stream = FileInputStream(filename)
            val type = filename.substringAfterLast('.')


            logger.debug("Loading${if (generateMipmaps) {
                " mipmapped"
            } else {
                ""
            }} texture from $filename")

            return if (type == "raw") {
                val path = Paths.get(filename)
                val infoFile = path.resolveSibling(path.fileName.toString().substringBeforeLast(".") + ".info")
                val dimensions = Files.lines(infoFile).toList().first().split(",").map { it.toLong() }.toLongArray()

                loadFromFileRaw(device,
                    commandPools, queue, transferQueue,
                    stream, type, dimensions)
            } else {
                loadFromFile(device,
                    commandPools, queue, transferQueue,
                    stream, type, linearMin, linearMax, generateMipmaps)
            }
        }

        /**
         * Loads a texture from a file given by a [stream], and allocates the [VulkanTexture] on [device].
         */
        fun loadFromFile(device: VulkanDevice,
                         commandPools: VulkanRenderer.CommandPools, queue: VkQueue, transferQueue: VkQueue,
                         stream: InputStream, type: String,
                         linearMin: Boolean, linearMax: Boolean,
                         generateMipmaps: Boolean = true): VulkanTexture {
            var bi: BufferedImage
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
                    bi = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                    bi.setRGB(0, 0, 1, 1, intArrayOf(255, 0, 0), 0, 1)
                }

            } else {
                try {
                    val reader = BufferedInputStream(stream)
                    bi = ImageIO.read(stream)
                    reader.close()

                } catch (e: Exception) {
                    logger.error("Could not read image: ${e.message}")
                    bi = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                    bi.setRGB(0, 0, 1, 1, intArrayOf(255, 0, 0), 0, 1)
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
                commandPools, queue, transferQueue,
                texWidth, texHeight, 1,
                if (bi.colorModel.hasAlpha()) {
                    VK_FORMAT_R8G8B8A8_SRGB
                } else {
                    VK_FORMAT_R8G8B8A8_SRGB
                }, mipmapLevels, linearMin, linearMax)

            tex.copyFrom(imageData)
            memFree(imageData)

            return tex
        }

        /**
         * Loads a texture from a raw file given by a [stream], and allocates the [VulkanTexture] on [device].
         */
        @Suppress("UNUSED_PARAMETER")
        fun loadFromFileRaw(device: VulkanDevice,
                            commandPools: VulkanRenderer.CommandPools, queue: VkQueue, transferQueue: VkQueue,
                            stream: InputStream, type: String, dimensions: LongArray): VulkanTexture {
            val imageData: ByteBuffer = ByteBuffer.allocateDirect((2 * dimensions[0] * dimensions[1] * dimensions[2]).toInt())
            val buffer = ByteArray(1024 * 1024)

            var bytesRead = stream.read(buffer)
            while (bytesRead > -1) {
                imageData.put(buffer)
                bytesRead = stream.read(buffer)
            }

            val tex = VulkanTexture(
                device,
                commandPools, queue, transferQueue,
                dimensions[0].toInt(), dimensions[1].toInt(), dimensions[2].toInt(),
                VK_FORMAT_R16_UINT, 1, true, true)

            tex.copyFrom(imageData)

            stream.close()

            return tex
        }

        /**
         * Converts a buffered image to an RGBA byte buffer.
         */
        protected fun bufferedImageToRGBABuffer(bufferedImage: BufferedImage): ByteBuffer {
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

        /**
         * Transitions Vulkan image layouts.
         */
        fun transitionLayout(image: VkImage, oldLayout: VkImageLayout, newLayout: VkImageLayout, mipLevels: Int = 1,
                             subresourceRange: VkImageSubresourceRange? = null,
                             srcStage: Int = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, dstStage: Int = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                             commandBuffer: VkCommandBuffer) {

            with(commandBuffer) {
                val barrier = VkImageMemoryBarrier.calloc(1)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .pNext(NULL)
                    .oldLayout(oldLayout.i)
                    .newLayout(newLayout.i)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image.L)

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

                if (oldLayout == VkImageLayout.PREINITIALIZED && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    barrier
                        .srcAccessMask(VK_ACCESS_HOST_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                } else if (oldLayout == VkImageLayout.PREINITIALIZED && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL) {
                    barrier
                        .srcAccessMask(VK_ACCESS_HOST_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                } else if (oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL) {
                    barrier
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                } else if (oldLayout == VkImageLayout.UNDEFINED && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL) {
                    barrier
                        .srcAccessMask(0)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                } else if (oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    barrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL) {
                    barrier
                        .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                } else if (oldLayout == VkImageLayout.PRESENT_SRC_KHR && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    barrier
                        .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.PRESENT_SRC_KHR) {
                    barrier
                        .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                } else if (oldLayout == VkImageLayout.UNDEFINED && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL) {
                    barrier
                        .srcAccessMask(0)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                } else if (oldLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                } else if (oldLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                } else if (oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                } else if (oldLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                } else if (oldLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_INPUT_ATTACHMENT_READ_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                } else if (oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                } else if (oldLayout == VkImageLayout.PRESENT_SRC_KHR && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                } else if (oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.PRESENT_SRC_KHR) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                } else if (oldLayout == VkImageLayout.UNDEFINED && newLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL) {
                    barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                } else {
                    logger.error("Unsupported layout transition: $oldLayout -> $newLayout")
                }

                logger.trace("Transition: {} -> {} with srcAccessMark={}, dstAccessMask={}, srcStage={}, dstStage={}", oldLayout, newLayout, barrier.srcAccessMask(), barrier.dstAccessMask(), srcStage, dstStage)

                vkCmdPipelineBarrier(this,
                    srcStage,
                    dstStage,
                    0, null, null, barrier)

                barrier.free()
            }
        }

        private fun GenericTexture.toVulkanFormat(): Int {
            return when (this.type) {
                GLTypeEnum.Byte -> when (this.channels) {
                    1 -> VK_FORMAT_R8_SNORM
                    2 -> VK_FORMAT_R8G8_SNORM
                    3 -> VK_FORMAT_R8G8B8A8_SNORM
                    4 -> VK_FORMAT_R8G8B8A8_SNORM

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VK_FORMAT_R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.UnsignedByte -> when (this.channels) {
                    1 -> VK_FORMAT_R8_UNORM
                    2 -> VK_FORMAT_R8G8_UNORM
                    3 -> VK_FORMAT_R8G8B8A8_UNORM
                    4 -> VK_FORMAT_R8G8B8A8_UNORM

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VK_FORMAT_R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.Short -> when (this.channels) {
                    1 -> VK_FORMAT_R16_SNORM
                    2 -> VK_FORMAT_R16G16_SNORM
                    3 -> VK_FORMAT_R16G16B16A16_SNORM
                    4 -> VK_FORMAT_R16G16B16A16_SNORM

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VK_FORMAT_R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.UnsignedShort -> when (this.channels) {
                    1 -> VK_FORMAT_R16_UNORM
                    2 -> VK_FORMAT_R16G16_UNORM
                    3 -> VK_FORMAT_R16G16B16A16_UNORM
                    4 -> VK_FORMAT_R16G16B16A16_UNORM

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VK_FORMAT_R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.Int -> when (this.channels) {
                    1 -> VK_FORMAT_R32_SINT
                    2 -> VK_FORMAT_R32G32_SINT
                    3 -> VK_FORMAT_R32G32B32A32_SINT
                    4 -> VK_FORMAT_R32G32B32A32_SINT

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VK_FORMAT_R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.UnsignedInt -> when (this.channels) {
                    1 -> VK_FORMAT_R32_UINT
                    2 -> VK_FORMAT_R32G32_UINT
                    3 -> VK_FORMAT_R32G32B32A32_UINT
                    4 -> VK_FORMAT_R32G32B32A32_UINT

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VK_FORMAT_R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.Float -> when (this.channels) {
                    1 -> VK_FORMAT_R32_SFLOAT
                    2 -> VK_FORMAT_R32G32_SFLOAT
                    3 -> VK_FORMAT_R32G32B32A32_SFLOAT
                    4 -> VK_FORMAT_R32G32B32A32_SFLOAT

                    else -> {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default"); VK_FORMAT_R8G8B8A8_UNORM
                    }
                }

                GLTypeEnum.Double -> TODO("Double format textures are not supported")
            }
        }
    }

    /**
     * Deallocates and destroys this [VulkanTexture] instance, freeing all memory
     * related to it.
     */
    override fun close() {
        if (image.view != -1L) {
            vkDestroyImageView(device.vulkanDevice, image.view, null)
            image.view = -1L
        }

        if (image.image.isValid) {
            vkDev destroyImage image.image
            image.image = VkImage(NULL)
        }

        if (image.sampler != -1L) {
            vkDestroySampler(device.vulkanDevice, image.sampler, null)
            image.sampler = -1L
        }

        if (image.memory != -1L) {
            vkFreeMemory(device.vulkanDevice, image.memory, null)
            image.memory = -1L
        }

        if (stagingImage.image.isValid) {
            vkDev destroyImage stagingImage.image
            stagingImage.image = VkImage(NULL)
        }

        if (stagingImage.memory != -1L) {
            vkFreeMemory(device.vulkanDevice, stagingImage.memory, null)
            stagingImage.memory = -1L
        }
    }


}
