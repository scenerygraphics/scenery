package graphics.scenery.backends.vulkan

import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import java.nio.ByteBuffer
import java.nio.LongBuffer
import graphics.scenery.Hub
import graphics.scenery.utils.SceneryPanel
import org.lwjgl.system.MemoryStack


/**
 * Extended Vulkan swapchain that runs in headless mode.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class HeadlessSwapchain(device: VulkanDevice,
                        queue: VkQueue,
                        commandPool: Long,
                        renderConfig: RenderConfigReader.RenderConfig,
                        useSRGB: Boolean = true,
                        @Suppress("unused") val useFramelock: Boolean = false,
                        @Suppress("unused") val bufferCount: Int = 2) : VulkanSwapchain(device, queue, commandPool, renderConfig, useSRGB) {
    protected var initialized = false
    protected lateinit var sharingBuffer: VulkanBuffer
    protected lateinit var imageBuffer: ByteBuffer

    protected var imagePanel: SceneryPanel? = null

    protected lateinit var vulkanInstance: VkInstance
    protected lateinit var vulkanSwapchainRecreator: VulkanRenderer.SwapchainRecreator

    protected val WINDOW_RESIZE_TIMEOUT = 400 * 10e6

    inner class ResizeHandler {
        @Volatile
        var lastResize = -1L
        var lastWidth = 0
        var lastHeight = 0

        @Synchronized
        fun queryResize() {
            if (lastWidth <= 0 || lastHeight <= 0) {
                lastWidth = Math.max(1, lastWidth)
                lastHeight = Math.max(1, lastHeight)
                return
            }

            if (lastResize > 0L && lastResize + WINDOW_RESIZE_TIMEOUT < System.nanoTime()) {
                lastResize = System.nanoTime()
                return
            }

            if (lastWidth == window.width && lastHeight == window.height) {
                return
            }

            window.width = lastWidth
            window.height = lastHeight

            vulkanSwapchainRecreator.mustRecreate = true

            lastResize = -1L
        }
    }

    protected var resizeHandler = ResizeHandler()

    override fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow {
        vulkanInstance = device.instance
        vulkanSwapchainRecreator = swapchainRecreator
        window = SceneryWindow.HeadlessWindow()

        window.width = win.width
        window.height = win.height

        return window
    }

    override fun create(oldSwapchain: Swapchain?): Swapchain {
        if (oldSwapchain != null && initialized) {
            MemoryUtil.memFree(imageBuffer)
            sharingBuffer.close()
        }

        val format = if(useSRGB) {
            VK10.VK_FORMAT_B8G8R8A8_SRGB
        } else {
            VK10.VK_FORMAT_B8G8R8A8_UNORM
        }
        presentQueue = VU.createDeviceQueue(device, device.queueIndices.graphicsQueue)

        val textureImages = (0 until bufferCount).map {
            val t = VulkanTexture(device, commandPool, queue, window.width, window.height, 1,
                format, 1)
            val image = t.createImage(window.width, window.height, 1, format,
                VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_IMAGE_TILING_OPTIMAL, VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 1)
            t to image
        }

        images = textureImages.map {
            it.second.image
        }.toLongArray()

        imageViews = textureImages.map {
            it.first.createImageView(it.second, format)
        }.toLongArray()

        logger.info("Created ${images?.size} swapchain images")

        val imageByteSize = window.width * window.height * 4L
        imageBuffer = MemoryUtil.memAlloc(imageByteSize.toInt())
        sharingBuffer = VulkanBuffer(device,
            imageByteSize,
            VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            wantAligned = true)

        imagePanel?.prefWidth = window.width.toDouble()
        imagePanel?.prefHeight = window.height.toDouble()

        resizeHandler.lastWidth = window.width
        resizeHandler.lastHeight = window.height

        initialized = true

        return this
    }

    var currentImage = 0
    override fun next(timeout: Long, signalSemaphore: Long): Boolean {
        MemoryStack.stackPush().use { stack ->
            VK10.vkQueueWaitIdle(presentQueue)

            val signal = stack.callocLong(1)
            signal.put(0, signalSemaphore)

            with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
                this.endCommandBuffer(device, commandPool, presentQueue, signalSemaphores = signal,
                    flush = true, dealloc = true)
            }

            currentImage = ++currentImage % (images?.size ?: 1)
        }

        return false
    }

    override fun present(waitForSemaphores: LongBuffer?) {
        MemoryStack.stackPush().use { stack ->
            if (vulkanSwapchainRecreator.mustRecreate) {
                return
            }

            val mask = stack.callocInt(1)
            mask.put(0, VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)

            with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
                this.endCommandBuffer(device, commandPool, presentQueue,
                    waitSemaphores = waitForSemaphores, waitDstStageMask = mask,
                    flush = true, dealloc = true)
            }
        }
    }

    override fun postPresent(image: Int) {
        if (vulkanSwapchainRecreator.mustRecreate && sharingBuffer.initialized()) {
            return
        }

        with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
            val subresource = VkImageSubresourceLayers.calloc()
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0)
                .baseArrayLayer(0)
                .layerCount(1)

            val regions = VkBufferImageCopy.calloc(1)
                .bufferRowLength(0)
                .bufferImageHeight(0)
                .imageOffset(VkOffset3D.calloc().set(0, 0, 0))
                .imageExtent(VkExtent3D.calloc().set(window.width, window.height, 1))
                .imageSubresource(subresource)

            val transferImage = images!![image]

            VulkanTexture.transitionLayout(transferImage,
                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                srcStage = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                dstStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                commandBuffer = this)

            VK10.vkCmdCopyImageToBuffer(this, transferImage,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                sharingBuffer.vulkanBuffer,
                regions)

            VulkanTexture.transitionLayout(transferImage,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                srcStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                dstStage = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                commandBuffer = this)

            this.endCommandBuffer(device, commandPool, queue,
                flush = true, dealloc = true)
        }

        VK10.vkQueueWaitIdle(queue)

        resizeHandler.queryResize()
    }

    override fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator) {
        window.isFullscreen = !window.isFullscreen

        resizeHandler.lastWidth = window.width
        resizeHandler.lastHeight = window.height
    }

    override fun embedIn(panel: SceneryPanel?) {
        imagePanel = panel
    }

    override fun close() {
        KHRSwapchain.vkDestroySwapchainKHR(device.vulkanDevice, handle, null)
        KHRSurface.vkDestroySurfaceKHR(device.instance, surface, null)

        presentInfo.free()

        MemoryUtil.memFree(swapchainImage)
        MemoryUtil.memFree(swapchainPointer)
        MemoryUtil.memFree(imageBuffer)

        sharingBuffer.close()
    }
}
