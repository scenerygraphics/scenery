package graphics.scenery.backends.vulkan

import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import javafx.stage.Stage
import java.util.concurrent.CountDownLatch
import com.sun.javafx.application.PlatformImpl
import graphics.scenery.Hub
import graphics.scenery.utils.SceneryPanel
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.VPos
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import java.util.concurrent.locks.ReentrantLock


/**
 * Vulkan swapchain compatible with JavaFX
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class FXSwapchain(val window: SceneryWindow,
                  val device: VkDevice,
                  val physicalDevice: VkPhysicalDevice,
                  val memoryProperties: VkPhysicalDeviceMemoryProperties,
                  val queue: VkQueue,
                  val commandPool: Long,
                  val renderConfig: RenderConfigReader.RenderConfig,
                  val useSRGB: Boolean = true,
                  val useFramelock: Boolean = false,
                  val bufferCount: Int = 2) : Swapchain {
    override var handle: Long = 0L
    override var images: LongArray? = null
    override var imageViews: LongArray? = null
    override var format: Int = 0

    var swapchainImage: IntBuffer = MemoryUtil.memAllocInt(1)
    var swapchainPointer: LongBuffer = MemoryUtil.memAllocLong(1)
    var presentInfo: VkPresentInfoKHR = VkPresentInfoKHR.calloc()
    lateinit var sharingBuffer: VulkanBuffer
    lateinit var imageBuffer: ByteBuffer
    var lock = ReentrantLock()

    private var glfwOffscreenWindow: Long = -1L
    lateinit private var stage: Stage
    lateinit private var imagePanel: SceneryPanel

    var surface: Long = 0

    lateinit var vulkanInstance: VkInstance
    lateinit var vulkanSwapchainRecreator: VulkanRenderer.SwapchainRecreator

    private val WINDOW_RESIZE_TIMEOUT = 400 * 10e6

    val logger = LoggerFactory.getLogger("FXSwapchain")

    inner class ResizeHandler {
        @Volatile var lastResize = -1L
        var lastWidth = window.width
        var lastHeight = window.height

        private var lock = ReentrantLock()

        @Synchronized fun queryResize() {
            if (lastWidth <= 0 || lastHeight <= 0) {
                lastWidth = Math.max(1, lastWidth)
                lastHeight = Math.max(1, lastHeight)
                return
            }

            if (lastResize > 0L && lastResize + WINDOW_RESIZE_TIMEOUT < System.nanoTime()) {
                lastResize = System.nanoTime()
                return
            }

            if(lastWidth == window.width && lastHeight == window.height) {
                return
            }

            if (lock.tryLock()) {
                window.width = lastWidth
                window.height = lastHeight

                vulkanSwapchainRecreator.mustRecreate = true

                lastResize = -1L
                lock.unlock()
            }
        }
    }

    var resizeHandler = ResizeHandler()

    override fun createWindow(window: SceneryWindow, instance: VkInstance, swapchainRecreator: VulkanRenderer.SwapchainRecreator) {
        vulkanInstance = instance
        vulkanSwapchainRecreator = swapchainRecreator

        PlatformImpl.startup { }
        val lCountDownLatch = CountDownLatch(1)
        Platform.runLater({
            stage = Stage()
            stage.title = "FXSwapchain"
            window.javafxStage = stage

            val lStackPane = StackPane()
            lStackPane.backgroundProperty()
                .set(Background(BackgroundFill(Color.TRANSPARENT,
                    CornerRadii.EMPTY,
                    Insets.EMPTY)))

            val pane = GridPane()
            val label = Label("Experimental JavaFX Swapchain - use with caution!")

            imagePanel = SceneryPanel(window.width, window.height)

            resizeHandler.lastWidth = window.width
            resizeHandler.lastHeight = window.height

            imagePanel.widthProperty().addListener { _, _, newWidth ->
                logger.info("New width: $newWidth")
                resizeHandler.lastWidth = newWidth.toInt()
            }

            imagePanel.heightProperty().addListener { _, _, newHeight ->
                logger.info("New height: $newHeight")
                resizeHandler.lastHeight = newHeight.toInt()
            }

            imagePanel.minWidth = 100.0
            imagePanel.minHeight = 100.0
            imagePanel.prefWidth = window.width.toDouble()
            imagePanel.prefHeight = window.height.toDouble()

            GridPane.setHgrow(imagePanel, Priority.ALWAYS)
            GridPane.setVgrow(imagePanel, Priority.ALWAYS)

            GridPane.setFillHeight(imagePanel, true)
            GridPane.setFillWidth(imagePanel, true)

            GridPane.setHgrow(label, Priority.ALWAYS)
            GridPane.setHalignment(label, HPos.CENTER)
            GridPane.setValignment(label, VPos.BOTTOM)

            label.maxWidthProperty().bind(pane.widthProperty())
            pane.style = """
            -fx-background-color: linear-gradient(
                        from 0px .75em to .75em 0px,
                        repeat,
                        rgba(25, 25, 12, 0.6) 0%,
                        rgba(25, 25, 12, 0.6) 49%,
                        derive(rgb(228, 205, 0, 0.6), 30%) 50%,
                        derive(rgb(228, 205, 0, 0.6), 30%) 99%);
            -fx-font-family: Consolas;
            -fx-font-weight: 400;
            -fx-font-size: 1.2em;
            -fx-text-fill: white;
            -fx-text-alignment: center;
            """
            label.style = """
            -fx-padding: 0.2em;
            -fx-background-color: rgba(228, 205, 0, 0.6);
            -fx-text-fill: black;
            """
            label.textAlignment = TextAlignment.CENTER

            pane.add(imagePanel, 1, 1)
            pane.add(label, 1, 2)
            lStackPane.children.addAll(pane)

            val scene = Scene(lStackPane)
            stage.scene = scene
            stage.show()
            lCountDownLatch.countDown()

            stage.onCloseRequest = EventHandler { window.shouldClose = true }

            try {
                lCountDownLatch.await()
            } catch (e: InterruptedException) {
            }
        })
    }

    override fun create(oldSwapchain: Swapchain?): Swapchain {
        if (glfwOffscreenWindow != -1L) {
            glfwDestroyWindow(glfwOffscreenWindow)
        }

        // create off-screen, undecorated GLFW window
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)

        logger.info("Creating window: ${window.width}/${window.height}")
        glfwOffscreenWindow = glfwCreateWindow(window.width, window.height, "scenery", MemoryUtil.NULL, MemoryUtil.NULL)
        val w = intArrayOf(1)
        val h = intArrayOf(1)
        glfwGetWindowSize(glfwOffscreenWindow, w, h)
        logger.info("actual w/h: ${w[0]}/${h[0]}")

        surface = VU.run(MemoryUtil.memAllocLong(1), "glfwCreateWindowSurface") {
            GLFWVulkan.glfwCreateWindowSurface(vulkanInstance, glfwOffscreenWindow, null, this)
        }

        val colorFormatAndSpace = getColorFormatAndSpace()

        var err: Int
        // Get physical device surface properties and formats
        val surfCaps = VkSurfaceCapabilitiesKHR.calloc()
        err = KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, surfCaps)
        if (err != VK10.VK_SUCCESS) {
            throw AssertionError("Failed to get physical device surface capabilities: " + VU.translate(err))
        }

        val pPresentModeCount = MemoryUtil.memAllocInt(1)
        err = KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, null)
        val presentModeCount = pPresentModeCount.get(0)
        if (err != VK10.VK_SUCCESS) {
            throw AssertionError("Failed to get number of physical device surface presentation modes: " + VU.translate(err))
        }

        val pPresentModes = MemoryUtil.memAllocInt(presentModeCount)
        err = KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, pPresentModes)
        MemoryUtil.memFree(pPresentModeCount)
        if (err != VK10.VK_SUCCESS) {
            throw AssertionError("Failed to get physical device surface presentation modes: " + VU.translate(err))
        }

        // Try to use mailbox mode. Low latency and non-tearing
        var swapchainPresentMode = KHRSurface.VK_PRESENT_MODE_FIFO_KHR
        for (i in 0..presentModeCount - 1) {
            if (pPresentModes.get(i) == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR) {
                swapchainPresentMode = KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR
                break
            }
            if (swapchainPresentMode != KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR && pPresentModes.get(i) == KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR) {
                swapchainPresentMode = KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR
            }
        }
        MemoryUtil.memFree(pPresentModes)

        // Determine the number of images
        var desiredNumberOfSwapchainImages = surfCaps.minImageCount() + 1
        if (surfCaps.maxImageCount() in 1..(desiredNumberOfSwapchainImages - 1)) {
            desiredNumberOfSwapchainImages = surfCaps.maxImageCount()
        }

        val currentExtent = surfCaps.currentExtent()
        val currentWidth = currentExtent.width()
        val currentHeight = currentExtent.height()

        if (currentWidth > 0 && currentHeight > 0) {
            window.width = currentWidth
            window.height = currentHeight
        } else {
            // TODO: Better default values
            window.width = 1920
            window.height = 1200
        }

        val preTransform: Int
        if (surfCaps.supportedTransforms() and KHRSurface.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR != 0) {
            preTransform = KHRSurface.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR
        } else {
            preTransform = surfCaps.currentTransform()
        }
        surfCaps.free()

        val swapchainCI = VkSwapchainCreateInfoKHR.calloc()
            .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
            .pNext(MemoryUtil.NULL)
            .surface(surface)
            .minImageCount(desiredNumberOfSwapchainImages)
            .imageFormat(colorFormatAndSpace.colorFormat)
            .imageColorSpace(colorFormatAndSpace.colorSpace)
            .imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
            .preTransform(preTransform)
            .imageArrayLayers(1)
            .imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
            .pQueueFamilyIndices(null)
            .presentMode(swapchainPresentMode)
            .clipped(true)
            .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)

        if (oldSwapchain is VulkanSwapchain) {
            swapchainCI.oldSwapchain(oldSwapchain.handle)
        }

        swapchainCI.imageExtent().width(window.width).height(window.height)
        val pSwapChain = MemoryUtil.memAllocLong(1)
        err = KHRSwapchain.vkCreateSwapchainKHR(device, swapchainCI, null, pSwapChain)
        swapchainCI.free()

        val swapChain = pSwapChain.get(0)
        MemoryUtil.memFree(pSwapChain)
        if (err != VK10.VK_SUCCESS) {
            throw AssertionError("Failed to create swap chain: " + VU.translate(err))
        }

        // If we just re-created an existing swapchain, we should destroy the old swapchain at this point.
        // Note: destroying the swapchain also cleans up all its associated presentable images once the platform is done with them.
        if (oldSwapchain is VulkanSwapchain && oldSwapchain.handle != VK10.VK_NULL_HANDLE) {
            KHRSwapchain.vkDestroySwapchainKHR(device, oldSwapchain.handle, null)
        }

        val pImageCount = MemoryUtil.memAllocInt(1)
        err = KHRSwapchain.vkGetSwapchainImagesKHR(device, swapChain, pImageCount, null)
        val imageCount = pImageCount.get(0)
        if (err != VK10.VK_SUCCESS) {
            throw AssertionError("Failed to get number of swapchain images: " + VU.translate(err))
        }

        val pSwapchainImages = MemoryUtil.memAllocLong(imageCount)
        err = KHRSwapchain.vkGetSwapchainImagesKHR(device, swapChain, pImageCount, pSwapchainImages)
        if (err != VK10.VK_SUCCESS) {
            throw AssertionError("Failed to get swapchain images: " + VU.translate(err))
        }
        MemoryUtil.memFree(pImageCount)

        val images = LongArray(imageCount)
        val imageViews = LongArray(imageCount)
        val colorAttachmentView = VkImageViewCreateInfo.calloc()
            .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .pNext(MemoryUtil.NULL)
            .format(colorFormatAndSpace.colorFormat)
            .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
            .flags(0)

        colorAttachmentView.components()
            .r(VK10.VK_COMPONENT_SWIZZLE_R)
            .g(VK10.VK_COMPONENT_SWIZZLE_G)
            .b(VK10.VK_COMPONENT_SWIZZLE_B)
            .a(VK10.VK_COMPONENT_SWIZZLE_A)

        colorAttachmentView.subresourceRange()
            .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1)

        with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
            for (i in 0..imageCount - 1) {
                images[i] = pSwapchainImages.get(i)

                VU.setImageLayout(this, images[i],
                    aspectMask = VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                    oldImageLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                    newImageLayout = KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                colorAttachmentView.image(images[i])

                imageViews[i] = VU.run(MemoryUtil.memAllocLong(1), "create image view",
                    { VK10.vkCreateImageView(device, colorAttachmentView, null, this) })
            }

            this.endCommandBuffer(device, commandPool, queue,
                flush = true, dealloc = true)
        }

        colorAttachmentView.free()
        MemoryUtil.memFree(pSwapchainImages)

        val imageByteSize = window.width * window.height * 4L
        imageBuffer = MemoryUtil.memAlloc(imageByteSize.toInt())
        sharingBuffer = VU.createBuffer(device,
            memoryProperties, VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            wantAligned = true,
            allocationSize = imageByteSize)

        imagePanel.prefWidth = currentWidth.toDouble()
        imagePanel.prefHeight = currentHeight.toDouble()

//        imagePanel.resize(currentWidth.toDouble(), currentHeight.toDouble())

        resizeHandler.lastWidth = currentWidth
        resizeHandler.lastHeight = currentHeight

        logger.info("Final surface size is ${window.width}/${window.height}")

        this.images = images
        this.imageViews = imageViews
        this.handle = swapChain
        this.format = colorFormatAndSpace.colorFormat

        return this
    }

    private fun getColorFormatAndSpace(): VulkanSwapchain.ColorFormatAndSpace {
        val pQueueFamilyPropertyCount = MemoryUtil.memAllocInt(1)
        VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyPropertyCount, null)
        val queueCount = pQueueFamilyPropertyCount.get(0)
        val queueProps = VkQueueFamilyProperties.calloc(queueCount)
        VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyPropertyCount, queueProps)
        MemoryUtil.memFree(pQueueFamilyPropertyCount)

        // Iterate over each queue to learn whether it supports presenting:
        val supportsPresent = MemoryUtil.memAllocInt(queueCount)
        for (i in 0..queueCount - 1) {
            supportsPresent.position(i)
            val err = KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, supportsPresent)
            if (err != VK10.VK_SUCCESS) {
                throw AssertionError("Failed to physical device surface support: " + VU.translate(err))
            }
        }

        // Search for a graphics and a present queue in the array of queue families, try to find one that supports both
        var graphicsQueueNodeIndex = Integer.MAX_VALUE
        var presentQueueNodeIndex = Integer.MAX_VALUE
        for (i in 0..queueCount - 1) {
            if (queueProps.get(i).queueFlags() and VK10.VK_QUEUE_GRAPHICS_BIT != 0) {
                if (graphicsQueueNodeIndex == Integer.MAX_VALUE) {
                    graphicsQueueNodeIndex = i
                }
                if (supportsPresent.get(i) == VK10.VK_TRUE) {
                    graphicsQueueNodeIndex = i
                    presentQueueNodeIndex = i
                    break
                }
            }
        }
        queueProps.free()
        if (presentQueueNodeIndex == Integer.MAX_VALUE) {
            // If there's no queue that supports both present and graphics try to find a separate present queue
            for (i in 0..queueCount - 1) {
                if (supportsPresent.get(i) == VK10.VK_TRUE) {
                    presentQueueNodeIndex = i
                    break
                }
            }
        }
        MemoryUtil.memFree(supportsPresent)

        // Generate error if could not find both a graphics and a present queue
        if (graphicsQueueNodeIndex == Integer.MAX_VALUE) {
            throw AssertionError("No graphics queue found")
        }
        if (presentQueueNodeIndex == Integer.MAX_VALUE) {
            throw AssertionError("No presentation queue found")
        }
        if (graphicsQueueNodeIndex != presentQueueNodeIndex) {
            throw AssertionError("Presentation queue != graphics queue")
        }

        // Get list of supported formats
        val pFormatCount = MemoryUtil.memAllocInt(1)
        var err = KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, null)
        val formatCount = pFormatCount.get(0)
        if (err != VK10.VK_SUCCESS) {
            throw AssertionError("Failed to query number of physical device surface formats: " + VU.translate(err))
        }

        val surfFormats = VkSurfaceFormatKHR.calloc(formatCount)
        err = KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, surfFormats)
        MemoryUtil.memFree(pFormatCount)
        if (err != VK10.VK_SUCCESS) {
            throw AssertionError("Failed to query physical device surface formats: " + VU.translate(err))
        }

        val colorFormat: Int
        if (formatCount == 1 && surfFormats.get(0).format() == VK10.VK_FORMAT_UNDEFINED) {
            colorFormat = if (useSRGB) {
                VK10.VK_FORMAT_B8G8R8A8_SRGB
            } else {
                VK10.VK_FORMAT_B8G8R8A8_UNORM
            }
        } else {
            colorFormat = if (useSRGB) {
                VK10.VK_FORMAT_B8G8R8A8_SRGB
            } else {
                VK10.VK_FORMAT_B8G8R8A8_UNORM
            }
        }

        val colorSpace = if (useSRGB) {
            KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
        } else {
            surfFormats.get(0).colorSpace()
        }

        surfFormats.free()

        return VulkanSwapchain.ColorFormatAndSpace(colorFormat, colorSpace)
    }

    override fun present(waitForSemaphores: LongBuffer?) {
        if(vulkanSwapchainRecreator.mustRecreate) {
            return
        }

        // Present the current buffer to the swap chain
        // This will display the image
        swapchainPointer.put(0, handle)

        // Info struct to present the current swapchain image to the display
        presentInfo
            .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            .pNext(MemoryUtil.NULL)
            .swapchainCount(swapchainPointer.remaining())
            .pSwapchains(swapchainPointer)
            .pImageIndices(swapchainImage)
            .pResults(null)

        waitForSemaphores?.let { presentInfo.pWaitSemaphores(it) }

        val err = KHRSwapchain.vkQueuePresentKHR(queue, presentInfo)
        if (err != VK10.VK_SUCCESS) {
            throw AssertionError("Failed to present the swapchain image: " + VU.translate(err))
        }
    }

    override fun postPresent(image: Int) {
        if (vulkanSwapchainRecreator.mustRecreate) {
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
                sharingBuffer.buffer,
                regions)

            VulkanTexture.transitionLayout(transferImage,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                commandBuffer = this)

            this.endCommandBuffer(device, commandPool, queue,
                flush = true, dealloc = true)
        }

        VK10.vkQueueWaitIdle(queue)

        Platform.runLater {
            if (lock.tryLock() && vulkanSwapchainRecreator.mustRecreate == false) {
                val imageByteSize = window.width * window.height * 4
                imagePanel.update(sharingBuffer.map().getByteBuffer(imageByteSize))
                lock.unlock()
            }
        }

        resizeHandler.queryResize()
    }

    override fun next(timeout: Long, waitForSemaphore: Long): Boolean {
        val err = KHRSwapchain.vkAcquireNextImageKHR(device, handle, timeout,
            waitForSemaphore,
            VK10.VK_NULL_HANDLE, swapchainImage)

        if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR || err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
            return true
        } else if (err != VK10.VK_SUCCESS) {
            throw AssertionError("Failed to acquire next swapchain image: " + VU.translate(err))
        }

        return false
    }

    override fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator) {
        PlatformImpl.runLater {
            stage.isFullScreen = !stage.isFullScreen
        }
    }

    override fun close() {
        KHRSwapchain.vkDestroySwapchainKHR(device, handle, null)

        presentInfo.free()
        MemoryUtil.memFree(swapchainImage)
        MemoryUtil.memFree(swapchainPointer)

        glfwDestroyWindow(glfwOffscreenWindow)
    }
}
