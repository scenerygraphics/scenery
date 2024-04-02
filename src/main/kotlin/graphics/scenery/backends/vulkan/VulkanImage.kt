package graphics.scenery.backends.vulkan

import graphics.scenery.backends.vulkan.VulkanTexture.Companion.formatToString
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.utils.lazyLogger
import org.joml.Vector4i
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*

/**
 * Wrapper class for holding on to raw Vulkan [image]s backed by [memory].
 */
class VulkanImage(
    val device: VulkanDevice,
    val width: Int,
    val height: Int,
    val depth: Int,
    val format: Int,
    val miplevels: Int,
    var image: Long = -1L,
    var memory: Long = -1L,
    val maxSize: Long = -1L
) {

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
    fun copyFrom(commandBuffer: VkCommandBuffer, buffer: VulkanBuffer, update: UpdatableTexture.TextureUpdate? = null, bufferOffset: Long = 0) {
        with(commandBuffer) {
            val bufferImageCopy = VkBufferImageCopy.calloc(1)

            bufferImageCopy.imageSubresource()
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0)
                .baseArrayLayer(0)
                .layerCount(1)
            bufferImageCopy.bufferOffset(bufferOffset)

            if(update != null) {
                logger.debug("Copying {} to {}", update.extents, buffer.vulkanBuffer.toHexString())
                bufferImageCopy.imageExtent().set(update.extents.w, update.extents.h, update.extents.d)
                bufferImageCopy.imageOffset().set(update.extents.x, update.extents.y, update.extents.z)
            } else {
                logger.debug("Copying {}x{}x{} to {}", width, height, depth, buffer.vulkanBuffer.toHexString())
                bufferImageCopy.imageExtent().set(width, height, depth)
                bufferImageCopy.imageOffset().set(0, 0, 0)
            }

            VK10.vkCmdCopyBufferToImage(
                this,
                buffer.vulkanBuffer,
                this@VulkanImage.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                bufferImageCopy
            )

            bufferImageCopy.free()
        }

        update?.let {
            it.consumed = true
        }
    }

    /**
     * Copies the content of the image to [buffer] from a series of [updates]. This gets executed
     * within a given [commandBuffer].
     */
    fun copyFrom(commandBuffer: VkCommandBuffer, buffer: VulkanBuffer, updates: List<UpdatableTexture.TextureUpdate>, bufferOffset: Long = 0) {
        logger.debug("Got {} texture updates for {}", updates.size, this)
        with(commandBuffer) {
            val bufferImageCopy = VkBufferImageCopy.calloc(1)
            var offset = bufferOffset

            updates.forEach { update ->
                val updateSize = update.contents.remaining()
                bufferImageCopy.imageSubresource()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)
                bufferImageCopy.bufferOffset(offset)

                bufferImageCopy.imageExtent().set(update.extents.w, update.extents.h, update.extents.d)
                bufferImageCopy.imageOffset().set(update.extents.x, update.extents.y, update.extents.z)

                logger.debug("Copying {} to {}", update.extents, buffer.vulkanBuffer.toHexString())
                VK10.vkCmdCopyBufferToImage(
                    this,
                    buffer.vulkanBuffer,
                    this@VulkanImage.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    bufferImageCopy
                )

                offset += updateSize
                update.consumed = true
            }

            bufferImageCopy.free()
        }
    }

    /**
     * Copies the content of the image from a given [VulkanImage], [image].
     * This gets executed within a given [commandBuffer].
     */
    fun copyFrom(commandBuffer: VkCommandBuffer, image: VulkanImage, extents: UpdatableTexture.TextureExtents? = null) {
        with(commandBuffer) {
            val subresource = VkImageSubresourceLayers.calloc()
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseArrayLayer(0)
                .mipLevel(0)
                .layerCount(1)

            val region = VkImageCopy.calloc(1)
                .srcSubresource(subresource)
                .dstSubresource(subresource)

            if(extents != null) {
                region.srcOffset().set(extents.x, extents.y, extents.z)
                region.dstOffset().set(extents.x, extents.y, extents.z)
                region.extent().set(extents.w, extents.h, extents.d)
            } else {
                region.srcOffset().set(0, 0, 0)
                region.dstOffset().set(0, 0, 0)
                region.extent().set(width, height, depth)
            }

            VK10.vkCmdCopyImage(
                this,
                image.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                this@VulkanImage.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                region
            )

            subresource.free()
            region.free()
        }
    }

    /**
     * Creates a [mipLevels] mipmaps of this [VulkanImage] based on the contents
     * of the zero miplevel of the current image. All operations will be executed on
     * a given [commandBuffer].
     */
    fun maybeCreateMipmaps(commandBuffer: VkCommandBuffer, mipLevels: Int) {
        if(mipLevels == 1) {
            return
        }

        val imageBlit = VkImageBlit.calloc(1)
        for (mipLevel in 1 until mipLevels) {
            imageBlit.srcSubresource().set(VK10.VK_IMAGE_ASPECT_COLOR_BIT, mipLevel - 1, 0, 1)
            imageBlit.srcOffsets(1).set(width shr (mipLevel - 1), height shr (mipLevel - 1), 1)

            val dstWidth = width shr mipLevel
            val dstHeight = height shr mipLevel

            if (dstWidth < 2 || dstHeight < 2) {
                break
            }

            imageBlit.dstSubresource().set(VK10.VK_IMAGE_ASPECT_COLOR_BIT, mipLevel, 0, 1)
            imageBlit.dstOffsets(1).set(width shr (mipLevel), height shr (mipLevel), 1)

            val mipSourceRange = VkImageSubresourceRange.calloc()
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseArrayLayer(0)
                .layerCount(1)
                .baseMipLevel(mipLevel - 1)
                .levelCount(1)

            val mipTargetRange = VkImageSubresourceRange.calloc()
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseArrayLayer(0)
                .layerCount(1)
                .baseMipLevel(mipLevel)
                .levelCount(1)

            if (mipLevel > 1) {
                VulkanTexture.transitionLayout(
                    image,
                    VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    subresourceRange = mipSourceRange,
                    srcStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    dstStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    commandBuffer = commandBuffer
                )
            }

            VulkanTexture.transitionLayout(
                image,
                VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                subresourceRange = mipTargetRange,
                srcStage = VK10.VK_PIPELINE_STAGE_HOST_BIT,
                dstStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                commandBuffer = commandBuffer
            )

            VK10.vkCmdBlitImage(
                commandBuffer,
                image,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                image,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                imageBlit,
                VK10.VK_FILTER_LINEAR
            )

            VulkanTexture.transitionLayout(
                image,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                subresourceRange = mipSourceRange,
                srcStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                dstStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                commandBuffer = commandBuffer
            )

            VulkanTexture.transitionLayout(
                image,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                subresourceRange = mipTargetRange,
                srcStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                dstStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                commandBuffer = commandBuffer
            )

            mipSourceRange.free()
            mipTargetRange.free()
        }

        imageBlit.free()
    }

    /**
     * Creates a Vulkan image view containing [miplevels] miplevels for this image, with components swizzled
     * according to [swizzle], which is null by default. Will erase the default view, if it exists.
     */
    fun createView(miplevels: Int = this.miplevels, swizzle: Vector4i? = null): Long {
        if(view != -1L) {
            VK10.vkDestroyImageView(device.vulkanDevice, view, null)
        }

        val subresourceRange = VkImageSubresourceRange.calloc().set(VK10.VK_IMAGE_ASPECT_COLOR_BIT, 0, miplevels, 0, 1)

        val vi = VkImageViewCreateInfo.calloc()
            .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .pNext(MemoryUtil.NULL)
            .image(image)
            .viewType(if (depth > 1) {
                VK10.VK_IMAGE_VIEW_TYPE_3D
            } else {
                VK10.VK_IMAGE_VIEW_TYPE_2D
            })
            .format(format)
            .subresourceRange(subresourceRange)

        swizzle?.let {s ->
            vi.components().set(
                s.x,
                s.y,
                s.z,
                s.w
            )
        }

        return VU.getLong("Creating image view",
                          { VK10.vkCreateImageView(device.vulkanDevice, vi, null, this) },
                          { vi.free(); subresourceRange.free(); })
    }

    /**
     * Returns a string representation of this [VulkanImage].
     */
    override fun toString(): String {
        return "VulkanImage (${this.image.toHexString()}, ${width}x${height}x${depth}, format=${format.formatToString()}, maxSize=${this.maxSize})"
    }
    
    companion object {
        private val logger by lazyLogger()

        /**
         * Creates a [VulkanImage] on [device], of [format] with a given [width], [height], and [depth].
         * [usage] and [memoryFlags] need to be given, as well as the [tiling] parameter and number of [mipLevels].
         * A custom memory allocator may be used and given as [customAllocator].
         */
        fun create(
            device: VulkanDevice,
            width: Int,
            height: Int,
            depth: Int,
            format: Int,
            usage: Int,
            tiling: Int,
            memoryFlags: Int,
            mipLevels: Int,
            initialLayout: Int? = null,
            customAllocator: ((VkMemoryRequirements, Long) -> Long)? = null,
            imageCreateInfo: VkImageCreateInfo? = null
        ): VulkanImage {
            val imageInfo = if(imageCreateInfo != null) {
                imageCreateInfo
            } else {
                val i = VkImageCreateInfo.calloc()
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(
                        if(depth == 1) {
                            VK10.VK_IMAGE_TYPE_2D
                        } else {
                            VK10.VK_IMAGE_TYPE_3D
                        }
                    )
                    .mipLevels(mipLevels)
                    .arrayLayers(1)
                    .format(format)
                    .tiling(tiling)
                    .initialLayout(
                        if(depth == 1) {
                            VK10.VK_IMAGE_LAYOUT_PREINITIALIZED
                        } else {
                            VK10.VK_IMAGE_LAYOUT_UNDEFINED
                        }
                    )
                    .usage(usage)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .flags(VK10.VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT)

                i.extent().set(width, height, depth)
                i
            }

            if(initialLayout != null) {
                imageInfo.initialLayout(initialLayout)
            }

            val image = VU.getLong("create staging image",
                                   { VK10.vkCreateImage(device.vulkanDevice, imageInfo, null, this) }, {})

            val reqs = VkMemoryRequirements.calloc()
            VK10.vkGetImageMemoryRequirements(device.vulkanDevice, image, reqs)
            val memorySize = reqs.size()

            val memory = if(customAllocator == null) {
                val allocInfo = VkMemoryAllocateInfo.calloc()
                    .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .pNext(MemoryUtil.NULL)
                    .allocationSize(memorySize)
                    .memoryTypeIndex(device.getMemoryType(reqs.memoryTypeBits(), memoryFlags).first())

                VU.getLong("allocate image staging memory of size $memorySize",
                           { VK10.vkAllocateMemory(device.vulkanDevice, allocInfo, null, this) },
                           { imageInfo.free(); allocInfo.free() })
            } else {
                customAllocator.invoke(reqs, image)
            }

            reqs.free()

            VK10.vkBindImageMemory(device.vulkanDevice, image, memory, 0)

            return VulkanImage(device, width, height, depth, format, mipLevels, image, memory, memorySize)
        }
    }
}