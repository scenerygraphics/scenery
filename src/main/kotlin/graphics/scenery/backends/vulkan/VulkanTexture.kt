package graphics.scenery.backends.vulkan

import cleargl.GLTypeEnum
import cleargl.TGAReader
import glm_.L
import glm_.f
import glm_.i
import graphics.scenery.GenericTexture
import graphics.scenery.TextureExtents
import graphics.scenery.TextureUpdate
import graphics.scenery.utils.LazyLogger
import kool.free
import kool.rem
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkImageCreateInfo
import vkk.*
import vkk.entities.*
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
                         val format: VkFormat = VkFormat.R8G8B8_SRGB, var mipLevels: Int = 1,
                         val minFilterLinear: Boolean = true, val maxFilterLinear: Boolean = true) : AutoCloseable {
    //protected val logger by LazyLogger()

    /** The Vulkan image associated with this texture. */
    var image: VulkanImage
        protected set

    private var stagingImage: VulkanImage
    private var gt: GenericTexture? = null

    val vkDev get() = device.vulkanDevice

    /**
     * Wrapper class for holding on to raw Vulkan [image]s backed by [memory].
     */
    inner class VulkanImage(var image: VkImage = VkImage(NULL),
                            var memory: VkDeviceMemory = VkDeviceMemory(NULL),
                            val maxSize: VkDeviceSize = VkDeviceSize(0L)) {

        /** Raw Vulkan sampler. */
        var sampler = VkSampler(NULL)
            internal set
        /** Raw Vulkan view. */
        var view = VkImageView(NULL)
            internal set

        /**
         * Copies the content of the image from [buffer]. This gets executed
         * within a given [commandBuffer].
         */
        fun copyFrom(commandBuffer: VkCommandBuffer, buffer: VulkanBuffer, update: TextureUpdate? = null, bufferOffset: VkDeviceSize = VkDeviceSize(0)) {
            with(commandBuffer) {
                val bufferImageCopy = vk.BufferImageCopy {
                    imageSubresource.apply {
                        aspectMask = VkImageAspect.COLOR_BIT.i
                        mipLevel = 0
                        baseArrayLayer = 0
                        layerCount = 1
                    }
                    this.bufferOffset = bufferOffset

                    if (update != null) {
                        imageExtent.set(update.extents.w, update.extents.h, update.extents.d)
                        imageOffset.set(update.extents.x, update.extents.y, update.extents.z)
                    } else {
                        imageExtent.set(width, height, depth)
                        imageOffset.set(0, 0, 0)
                    }
                }
                copyBufferToImage(buffer.vulkanBuffer,
                    this@VulkanImage.image, VkImageLayout.TRANSFER_DST_OPTIMAL,
                    bufferImageCopy)
            }

            update?.consumed = true
        }

        /**
         * Copies the content of the image to [buffer] from a series of [updates]. This gets executed
         * within a given [commandBuffer].
         */
        fun copyFrom(commandBuffer: VkCommandBuffer, buffer: VulkanBuffer, updates: List<TextureUpdate>, bufferOffset: VkDeviceSize = VkDeviceSize(0)) {
            with(commandBuffer) {
                val bufferImageCopy = vk.BufferImageCopy()
                var offset = bufferOffset.L

                updates.forEach { update ->
                    val updateSize = update.contents.remaining()
                    bufferImageCopy.apply {
                        imageSubresource.apply {
                            aspectMask = VkImageAspect.COLOR_BIT.i
                            mipLevel = 0
                            baseArrayLayer = 0
                            layerCount = 1
                        }
                        this.bufferOffset = VkDeviceSize(offset)

                        imageExtent.set(update.extents.w, update.extents.h, update.extents.d)
                        imageOffset.set(update.extents.x, update.extents.y, update.extents.z)
                    }
                    copyBufferToImage(buffer.vulkanBuffer,
                        this@VulkanImage.image, VkImageLayout.TRANSFER_DST_OPTIMAL,
                        bufferImageCopy)

                    offset += updateSize
                    update.consumed = true
                }
            }
        }

        /**
         * Copies the content of the image from a given [VulkanImage], [image].
         * This gets executed within a given [commandBuffer].
         */
        fun copyFrom(commandBuffer: VkCommandBuffer, image: VulkanImage, extents: TextureExtents? = null) {
            with(commandBuffer) {
                val subresource = vk.ImageSubresourceLayers {
                    aspectMask = VkImageAspect.COLOR_BIT.i
                    baseArrayLayer = 0
                    mipLevel = 0
                    layerCount = 1
                }
                val region = vk.ImageCopy {
                    srcSubresource = subresource
                    dstSubresource = subresource
                    if (extents != null) {
                        srcOffset.set(extents.x, extents.y, extents.z)
                        dstOffset.set(extents.x, extents.y, extents.z)
                        extent.set(extents.w, extents.h, extents.d)
                    } else {
                        srcOffset.set(0, 0, 0)
                        dstOffset.set(0, 0, 0)
                        extent.set(width, height, depth)
                    }
                }

                copyImage(image.image, VkImageLayout.TRANSFER_SRC_OPTIMAL,
                    this@VulkanImage.image, VkImageLayout.TRANSFER_DST_OPTIMAL,
                    region)
            }
        }
    }

    init {
        stagingImage = when (depth) {
            1 -> createImage(width, height, depth,
                format, VkImageUsage.TRANSFER_SRC_BIT.i,
                VkImageTiling.LINEAR,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_CACHED_BIT,
                mipLevels = 1)
            else -> createImage(16, 16, 1,
                format, VkImageUsage.TRANSFER_SRC_BIT.i,
                VkImageTiling.LINEAR,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_CACHED_BIT,
                mipLevels = 1)
        }

        image = createImage(width, height, depth,
            format, VkImageUsage.TRANSFER_DST_BIT or VkImageUsage.SAMPLED_BIT or VkImageUsage.TRANSFER_SRC_BIT,
            VkImageTiling.OPTIMAL, VkMemoryProperty.DEVICE_LOCAL_BIT.i,
            mipLevels)

        if (image.sampler.isInvalid) {
            image.sampler = createSampler()
        }

        if (image.view.isInvalid) {
            image.view = createImageView(image, format)
        }
    }

    /**
     * Alternative constructor to create a [VulkanTexture] from a [GenericTexture].
     */
    @Suppress("USELESS_ELVIS", "UNNECESSARY_SAFE_CALL")
    constructor(device: VulkanDevice,
                commandPools: VulkanRenderer.CommandPools, queue: VkQueue, transferQueue: VkQueue,
                genericTexture: GenericTexture, mipLevels: Int = 1) : this(device,
        commandPools,
        queue,
        transferQueue,
        genericTexture.dimensions.x().i,
        genericTexture.dimensions.y().i,
        genericTexture.dimensions.z()?.i ?: 1,
        genericTexture.toVulkanFormat(),
        mipLevels, genericTexture.minFilterLinear, genericTexture.maxFilterLinear) {
        gt = genericTexture
    }

    /**
     * Creates a Vulkan image of [format] with a given [width], [height], and [depth].
     * [usage] and [memoryFlags] need to be given, as well as the [tiling] parameter and number of [mipLevels].
     * A custom memory allocator may be used and given as [customAllocator].
     */
    fun createImage(width: Int, height: Int, depth: Int, format: VkFormat,
                    usage: VkImageUsageFlags, tiling: VkImageTiling, memoryFlags: VkMemoryPropertyFlags,
                    mipLevels: Int, customAllocator: ((VkMemoryRequirements, VkImage) -> VkDeviceMemory)? = null,
                    imageCreateInfo: VkImageCreateInfo? = null): VulkanImage {
        val imageInfo = imageCreateInfo ?: VkImageCreateInfo().apply {
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

        val image = vkDev createImage imageInfo

        val reqs = vkDev getImageMemoryRequirements image
        val memorySize = reqs.size

        val memory = customAllocator?.invoke(reqs, image) ?: vkDev allocateMemory vk.MemoryAllocateInfo {
            allocationSize = memorySize
            memoryTypeIndex = device.getMemoryType(reqs.memoryTypeBits, memoryFlags).first()
        }

        vkDev.bindImageMemory(image, memory)

        return VulkanImage(image, memory, memorySize)
    }


    /**
     * Copies the data for this texture from a [ByteBuffer], [data].
     */
    fun copyFrom(data: ByteBuffer): VulkanTexture {
        if (depth == 1 && data.remaining() > stagingImage.maxSize.L) {
            logger.warn("Allocated image size for $this (${stagingImage.maxSize.L}) less than copy source size ${data.remaining()}.")
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
                        device.mappedMemory(stagingImage.memory, VkDeviceSize(0), sourceBufferSize) { dest ->
                            sourceBuffer copyTo dest
                        }
                        transitionLayout(stagingImage.image,
                            VkImageLayout.PREINITIALIZED,
                            VkImageLayout.TRANSFER_SRC_OPTIMAL, mipLevels,
                            srcStage = VkPipelineStage.HOST_BIT.i,
                            dstStage = VkPipelineStage.TRANSFER_BIT.i,
                            commandBuffer = this)
                        transitionLayout(image.image,
                            VkImageLayout.PREINITIALIZED,
                            VkImageLayout.TRANSFER_DST_OPTIMAL, mipLevels,
                            srcStage = VkPipelineStage.HOST_BIT.i,
                            dstStage = VkPipelineStage.TRANSFER_BIT.i,
                            commandBuffer = this)

                        image.copyFrom(this, stagingImage)

                        transitionLayout(image.image,
                            VkImageLayout.TRANSFER_DST_OPTIMAL,
                            VkImageLayout.SHADER_READ_ONLY_OPTIMAL, mipLevels,
                            srcStage = VkPipelineStage.TRANSFER_BIT.i,
                            dstStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                            commandBuffer = this)
                    } else {
                        val genericTexture = gt
                        val requiredCapacity = VkDeviceSize(if (genericTexture != null && genericTexture.hasConsumableUpdates()) {
                            genericTexture.updates.map {
                                if (!it.consumed) {
                                    it.contents.remaining()
                                } else {
                                    0
                                }
                            }.sum().toLong()
                        } else {
                            sourceBuffer.capacity().toLong()
                        })

                        val buffer = VulkanBuffer(this@VulkanTexture.device,
                            requiredCapacity,
                            VkBufferUsage.TRANSFER_SRC_BIT.i,
                            VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                            wantAligned = false)

                        transitionLayout(image.image,
                            VkImageLayout.UNDEFINED,
                            VkImageLayout.TRANSFER_DST_OPTIMAL, mipLevels,
                            srcStage = VkPipelineStage.HOST_BIT.i,
                            dstStage = VkPipelineStage.TRANSFER_BIT.i,
                            commandBuffer = this)

                        if (genericTexture != null) {
                            if (genericTexture.hasConsumableUpdates()) {
                                val updates = genericTexture.updates.filter { !it.consumed }
                                val contents = updates.map { it.contents }

                                buffer.copyFrom(contents)
                                image.copyFrom(this, buffer, updates)

                                genericTexture.clearConsumedUpdates()
                            } else {
                                buffer.copyFrom(sourceBuffer)
                                image.copyFrom(this, buffer)
                            }
                        } else {
                            image.copyFrom(this, buffer)
                        }

                        buffer.close()

                        transitionLayout(image.image,
                            VkImageLayout.TRANSFER_DST_OPTIMAL,
                            VkImageLayout.SHADER_READ_ONLY_OPTIMAL, mipLevels,
                            srcStage = VkPipelineStage.TRANSFER_BIT.i,
                            dstStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                            commandBuffer = this)
                    }
                }.submit(transferQueue, block = true).deallocate()
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
                        srcStage = VkPipelineStage.HOST_BIT.i,
                        dstStage = VkPipelineStage.TRANSFER_BIT.i)
                    image.copyFrom(this, buffer)
                    transitionLayout(image.image, VkImageLayout.TRANSFER_DST_OPTIMAL,
                        VkImageLayout.TRANSFER_SRC_OPTIMAL, 1, commandBuffer = this,
                        srcStage = VkPipelineStage.TRANSFER_BIT.i,
                        dstStage = VkPipelineStage.TRANSFER_BIT.i)
                }
                .submit(transferQueue, block = true)
                .deallocate()

            vkDev.newCommandBuffer(commandPools.Standard)
                .record {

                    val imageBlit = vk.ImageBlit()
                    for (mipLevel in 1 until mipLevels) {
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
                                srcStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                                dstStage = VkPipelineStage.TRANSFER_BIT.i,
                                commandBuffer = this)
                        }

                        transitionLayout(image.image,
                            VkImageLayout.UNDEFINED,
                            VkImageLayout.TRANSFER_DST_OPTIMAL,
                            subresourceRange = mipTargetRange,
                            srcStage = VkPipelineStage.HOST_BIT.i,
                            dstStage = VkPipelineStage.TRANSFER_BIT.i,
                            commandBuffer = this)

                        blitImage(
                            image.image, VkImageLayout.TRANSFER_SRC_OPTIMAL,
                            image.image, VkImageLayout.TRANSFER_DST_OPTIMAL,
                            imageBlit, VkFilter.LINEAR)

                        transitionLayout(image.image,
                            VkImageLayout.TRANSFER_SRC_OPTIMAL,
                            VkImageLayout.SHADER_READ_ONLY_OPTIMAL, subresourceRange = mipSourceRange,
                            srcStage = VkPipelineStage.TRANSFER_BIT.i,
                            dstStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                            commandBuffer = this)

                        transitionLayout(image.image,
                            VkImageLayout.TRANSFER_DST_OPTIMAL,
                            VkImageLayout.SHADER_READ_ONLY_OPTIMAL, subresourceRange = mipTargetRange,
                            srcStage = VkPipelineStage.TRANSFER_BIT.i,
                            dstStage = VkPipelineStage.FRAGMENT_SHADER_BIT.i,
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

        image.view = createImageView(image, format)

        return this
    }

    /**
     * Creates a Vulkan image view with [format] for an [image].
     */
    fun createImageView(image: VulkanImage, format: VkFormat): VkImageView {

        val vi = vk.ImageViewCreateInfo {
            this.image = image.image
            viewType = if (depth > 1) {
                VkImageViewType.`3D`
            } else {
                VkImageViewType.`2D`
            }
            this.format = format
            this.subresourceRange = subresourceRange
        }
        if (gt?.channels == 1 && depth > 1) {
            vi.components(rgba = VkComponentSwizzle.R)
        }
        return vkDev createImageView vi
    }

    /**
     * Creates a default sampler for this texture.
     */
    private fun createSampler(): VkSampler {
        val samplerInfo = vk.SamplerCreateInfo {
            magFilter = when {
                minFilterLinear && depth <= 1 -> VkFilter.LINEAR
                else -> VkFilter.NEAREST
            }
            minFilter = when {
                maxFilterLinear && depth <= 1 -> VkFilter.LINEAR
                else -> VkFilter.NEAREST
            }
            mipmapMode = when (depth) {
                1 -> VkSamplerMipmapMode.LINEAR
                else -> VkSamplerMipmapMode.NEAREST
            }
            addressModeUVW = when (depth) {
                1 -> VkSamplerAddressMode.REPEAT
                else -> VkSamplerAddressMode.CLAMP_TO_EDGE
            }
            mipLodBias = 0f
            anisotropyEnable = depth == 1
            maxAnisotropy = when (depth) {
                1 -> 8f
                else -> 1f
            }
            minLod = 0f
            maxLod = when (depth) {
                1 -> mipLevels.f
                else -> 0f
            }
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
            compareOp = VkCompareOp.NEVER
        }
        return vkDev createSampler samplerInfo
    }

    /**
     * Utility methods for [VulkanTexture].
     */
    companion object {
        @JvmStatic
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
                Math.min(levelsW, levelsH)
            } else {
                1
            }

            val tex = VulkanTexture(
                device,
                commandPools, queue, transferQueue,
                texWidth, texHeight, 1,
                if (bi.colorModel.hasAlpha()) {
                    VkFormat.R8G8B8A8_SRGB
                } else {
                    VkFormat.R8G8B8A8_SRGB
                }, mipmapLevels, linearMin, linearMax)

            tex.copyFrom(imageData)
            imageData.free()

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
                VkFormat.R16_UINT, 1, true, true)

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

            return memAlloc(data.size).apply {
                order(ByteOrder.nativeOrder())
                put(data, 0, data.size)
                rewind()
            }
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
            newImage.createGraphics().apply {
                transform(at)
                drawImage(image, 0, 0, null)
                dispose()
            }
            return newImage
        }

        /**
         * Transitions Vulkan image layouts.
         */
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

                if (subresourceRange == null) {
                    this.subresourceRange.apply {
                        aspectMask = VkImageAspect.COLOR_BIT.i
                        baseMipLevel = 0
                        levelCount(mipLevels)
                        baseArrayLayer(0)
                        layerCount = 1
                    }
                } else {
                    this.subresourceRange = subresourceRange
                }

                when {
                    oldLayout == VkImageLayout.PREINITIALIZED && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL -> {
                        srcAccessMask = VkAccess.HOST_WRITE_BIT.i
                        dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.PREINITIALIZED && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL -> {
                        srcAccessMask = VkAccess.HOST_WRITE_BIT.i
                        dstAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                    }
                    oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL -> {
                        srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                        dstAccessMask = VkAccess.SHADER_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.UNDEFINED && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL -> {
                        srcAccessMask = 0
                        dstAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                    }
                    oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL -> {
                        srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                        dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL -> {
                        srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                        dstAccessMask = VkAccess.SHADER_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.PRESENT_SRC_KHR && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL -> {
                        srcAccessMask = VkAccess.MEMORY_READ_BIT.i
                        dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.PRESENT_SRC_KHR -> {
                        srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                        dstAccessMask = VkAccess.MEMORY_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.UNDEFINED && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL -> {
                        srcAccessMask = 0
                        dstAccessMask = VkAccess.SHADER_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL -> {
                        srcAccessMask = VkAccess.SHADER_READ_BIT.i
                        dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL -> {
                        srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                        dstAccessMask = VkAccess.SHADER_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL -> {
                        srcAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                        dstAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                    }
                    oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL -> {
                        srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                        dstAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                    }
                    oldLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL -> {
                        srcAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                        dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL -> {
                        srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                        dstAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                    }
                    oldLayout == VkImageLayout.SHADER_READ_ONLY_OPTIMAL && newLayout == VkImageLayout.TRANSFER_DST_OPTIMAL -> {
                        srcAccessMask = VkAccess.INPUT_ATTACHMENT_READ_BIT.i
                        dstAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                    }
                    oldLayout == VkImageLayout.TRANSFER_DST_OPTIMAL && newLayout == VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> {
                        srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                        dstAccessMask = VkAccess.DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.i
                    }
                    oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> {
                        srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                        dstAccessMask = VkAccess.DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.i
                    }
                    oldLayout == VkImageLayout.PRESENT_SRC_KHR && newLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL -> {
                        srcAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                        dstAccessMask = VkAccess.TRANSFER_READ_BIT.i
                    }
                    oldLayout == VkImageLayout.TRANSFER_SRC_OPTIMAL && newLayout == VkImageLayout.PRESENT_SRC_KHR -> {
                        srcAccessMask = VkAccess.TRANSFER_READ_BIT.i
                        dstAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                    }
                    oldLayout == VkImageLayout.UNDEFINED && newLayout == VkImageLayout.COLOR_ATTACHMENT_OPTIMAL -> {
                        srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                        dstAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                    }
                    else -> logger.error("Unsupported layout transition: $oldLayout -> $newLayout")
                }
            }

            logger.trace("Transition: {} -> {} with srcAccessMark={}, dstAccessMask={}, srcStage={}, dstStage={}", oldLayout, newLayout, barrier.srcAccessMask, barrier.dstAccessMask, srcStage, dstStage)

            commandBuffer.pipelineBarrier(srcStage, dstStage, imageMemoryBarrier = barrier)
        }

        private fun GenericTexture.toVulkanFormat(): VkFormat {
            val format = when (type) {
                GLTypeEnum.Byte -> when (channels) {
                    1 -> VkFormat.R8_SNORM
                    2 -> VkFormat.R8G8_SNORM
                    3 -> VkFormat.R8G8B8A8_SNORM
                    4 -> VkFormat.R8G8B8A8_SNORM
                    else -> VkFormat.R8G8B8A8_UNORM.also {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default")
                    }
                }

                GLTypeEnum.UnsignedByte -> when (channels) {
                    1 -> VkFormat.R8_UNORM
                    2 -> VkFormat.R8G8_UNORM
                    3 -> VkFormat.R8G8B8A8_UNORM
                    4 -> VkFormat.R8G8B8A8_UNORM
                    else -> VkFormat.R8G8B8A8_UNORM.also {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default")
                    }
                }

                GLTypeEnum.Short -> when (channels) {
                    1 -> VkFormat.R16_SNORM
                    2 -> VkFormat.R16G16_SNORM
                    3 -> VkFormat.R16G16B16A16_SNORM
                    4 -> VkFormat.R16G16B16A16_SNORM
                    else -> VkFormat.R8G8B8A8_UNORM.also {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default")
                    }
                }

                GLTypeEnum.UnsignedShort -> when (channels) {
                    1 -> VkFormat.R16_UNORM
                    2 -> VkFormat.R16G16_UNORM
                    3 -> VkFormat.R16G16B16A16_UNORM
                    4 -> VkFormat.R16G16B16A16_UNORM
                    else -> VkFormat.R8G8B8A8_UNORM.also {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default")
                    }
                }

                GLTypeEnum.Int -> when (channels) {
                    1 -> VkFormat.R32_SINT
                    2 -> VkFormat.R32G32_SINT
                    3 -> VkFormat.R32G32B32A32_SINT
                    4 -> VkFormat.R32G32B32A32_SINT
                    else -> VkFormat.R8G8B8A8_UNORM.also {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default")
                    }
                }

                GLTypeEnum.UnsignedInt -> when (channels) {
                    1 -> VkFormat.R32_UINT
                    2 -> VkFormat.R32G32_UINT
                    3 -> VkFormat.R32G32B32A32_UINT
                    4 -> VkFormat.R32G32B32A32_UINT
                    else -> VkFormat.R8G8B8A8_UNORM.also {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default")
                    }
                }

                GLTypeEnum.Float -> when (channels) {
                    1 -> VkFormat.R32_SFLOAT
                    2 -> VkFormat.R32G32_SFLOAT
                    3 -> VkFormat.R32G32B32A32_SFLOAT
                    4 -> VkFormat.R32G32B32A32_SFLOAT
                    else -> VkFormat.R8G8B8A8_UNORM.also {
                        logger.warn("Unknown texture type: $type, with $channels channels, falling back to default")
                    }
                }

                GLTypeEnum.Double -> TODO("Double format textures are not supported")
            }

            return when {
                !normalized && type != GLTypeEnum.Float && type != GLTypeEnum.Byte && type != GLTypeEnum.Int -> VkFormat of format.i + 4
                else -> format
            }
        }
    }

    /**
     * Deallocates and destroys this [VulkanTexture] instance, freeing all memory
     * related to it.
     */
    override fun close() {
        if (image.view.isValid) {
            vkDev destroyImageView image.view
            image.view = VkImageView(NULL)
        }

        if (image.image.isValid) {
            vkDev destroyImage image.image
            image.image = VkImage(NULL)
        }

        if (image.sampler.isValid) {
            vkDev destroySampler image.sampler
            image.sampler = VkSampler(NULL)
        }

        if (image.memory.isValid) {
            vkDev freeMemory image.memory
            image.memory = VkDeviceMemory(NULL)
        }

        if (stagingImage.image.isValid) {
            vkDev destroyImage stagingImage.image
            stagingImage.image = VkImage(NULL)
        }

        if (stagingImage.memory.isValid) {
            vkDev freeMemory stagingImage.memory
            stagingImage.memory = VkDeviceMemory(NULL)
        }
    }
}
