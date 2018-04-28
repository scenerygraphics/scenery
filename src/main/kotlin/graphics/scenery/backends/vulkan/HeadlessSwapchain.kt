package graphics.scenery.backends.vulkan

import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import java.nio.ByteBuffer
import java.nio.LongBuffer
import graphics.scenery.Hub
import graphics.scenery.utils.SceneryPanel


/**
 * Extended Vulkan swapchain that runs in headless mode.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class HeadlessSwapchain(device: VulkanDevice,
                        queue: VkQueue,
                        commandPool: Long,
                        renderConfig: RenderConfigReader.RenderConfig,
                        useSRGB: Boolean = true,
                        @Suppress("unused") val useFramelock: Boolean = false,
                        @Suppress("unused") val bufferCount: Int = 2) : VulkanSwapchain(device, queue, commandPool, renderConfig, useSRGB) {
    lateinit var sharingBuffer: VulkanBuffer
    lateinit var imageBuffer: ByteBuffer

    private var glfwOffscreenWindow: Long = -1L
    private var imagePanel: SceneryPanel? = null

    lateinit var vulkanInstance: VkInstance
    lateinit var vulkanSwapchainRecreator: VulkanRenderer.SwapchainRecreator

    private val WINDOW_RESIZE_TIMEOUT = 400 * 10e6

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

    var resizeHandler = ResizeHandler()

    override fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow {
        vulkanInstance = device.instance
        vulkanSwapchainRecreator = swapchainRecreator
        window = SceneryWindow.HeadlessWindow()

        window.width = win.width
        window.height = win.height

        return window
    }

    override fun create(oldSwapchain: Swapchain?): Swapchain {
        if (glfwOffscreenWindow != -1L) {
            MemoryUtil.memFree(imageBuffer)
            sharingBuffer.close()

            // we have to get rid of the old swapchain, as we have already constructed a new surface
            if (oldSwapchain is FXSwapchain && oldSwapchain.handle != VK10.VK_NULL_HANDLE) {
                KHRSwapchain.vkDestroySwapchainKHR(device.vulkanDevice, oldSwapchain.handle, null)
            }

            KHRSurface.vkDestroySurfaceKHR(device.instance, surface, null)
            glfwDestroyWindow(glfwOffscreenWindow)
        }

        // create off-screen, undecorated GLFW window
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)

        glfwOffscreenWindow = glfwCreateWindow(window.width, window.height, "scenery", MemoryUtil.NULL, MemoryUtil.NULL)
        val w = intArrayOf(1)
        val h = intArrayOf(1)
        glfwGetWindowSize(glfwOffscreenWindow, w, h)

        surface = VU.getLong("glfwCreateWindowSurface",
            { GLFWVulkan.glfwCreateWindowSurface(vulkanInstance, glfwOffscreenWindow, null, this) }, {})

        super.create(null)

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

        return this
    }

    override fun present(waitForSemaphores: LongBuffer?) {
        if (vulkanSwapchainRecreator.mustRecreate) {
            return
        }

        super.present(waitForSemaphores)
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
                commandBuffer = this)

            VK10.vkCmdCopyImageToBuffer(this, transferImage,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                sharingBuffer.vulkanBuffer,
                regions)

            VulkanTexture.transitionLayout(transferImage,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
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

        glfwDestroyWindow(glfwOffscreenWindow)
    }
}
