package graphics.scenery.backends.vulkan

import graphics.scenery.Hub
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.SceneryPanel
import kool.free
import kool.rem
import kool.set
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.KHRSwapchain
import org.lwjgl.vulkan.VkPresentInfoKHR
import org.lwjgl.vulkan.VkQueue
import vkk.*
import vkk.entities.*
import java.nio.LongBuffer
import java.util.*

/**
 * GLFW-based default Vulkan Swapchain and window, residing on [device], associated with [queue].
 * Needs to be given [commandPools] to allocate command buffers from. [useSRGB] determines whether
 * the sRGB colorspace will be used, [vsync] determines whether vertical sync will be forced (swapping
 * rendered images in sync with the screen's frequency). [undecorated] determines whether the created
 * window will have the window system's default chrome or not.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanSwapchain(open val device: VulkanDevice,
                           open val queue: VkQueue,
                           open val commandPools: VulkanRenderer.CommandPools,
                           @Suppress("unused") open val renderConfig: RenderConfigReader.RenderConfig,
                           open val useSRGB: Boolean = true,
                           open val vsync: Boolean = false,
                           open val undecorated: Boolean = false) : Swapchain {
    protected val logger by LazyLogger()

    /** Swapchain handle. */
    override var handle = VkSwapchainKHR(NULL)
    /** Array for rendered images. */
    override var images = VkImageArray()
    /** Array for image views. */
    override var imageViews = VkImageViewArray()
    /** Number of frames presented with this swapchain. */
    protected var presentedFrames: Long = 0

    /** Color format for the swapchain images. */
    override var format = VkFormat.UNDEFINED

    /** Swapchain image. */
    var swapchainImage = 0
    /** Pointer to the current swapchain. */
    var swapchainPointer: LongBuffer = MemoryUtil.memAllocLong(1)
    /** Present info, allocated only once and reused. */
    var presentInfo: VkPresentInfoKHR = VkPresentInfoKHR.calloc()
    /** Vulkan queue used exclusively for presentation. */
    lateinit var presentQueue: VkQueue

    /** Surface of the window to render into. */
    open var surface = VkSurface(NULL)
    /** [SceneryWindow] instance we are using. */
    lateinit var window: SceneryWindow
    /** Callback to use upon window resizing. */
    lateinit var windowSizeCallback: GLFWWindowSizeCallback

    /** Time in ns of the last resize event. */
    var lastResize = -1L
    private val WINDOW_RESIZE_TIMEOUT = 200 * 10e6

    private val retiredSwapchains: Queue<Pair<VulkanDevice, VkSwapchainKHR>> = ArrayDeque()

    /**
     * Data class for summarising [colorFormat] and [colorSpace] information.
     */
    data class ColorFormatAndSpace(var colorFormat: VkFormat = VkFormat.UNDEFINED, var colorSpace: VkColorSpace = VkColorSpace.SRGB_NONLINEAR_KHR)

    val vkDev get() = device.vulkanDevice
    val phDev get() = device.physicalDevice

    /**
     * Creates a window for this swapchain, and initialiases [win] as [SceneryWindow.GLFWWindow].
     * Needs to be handed a [VulkanRenderer.SwapchainRecreator].
     * Returns the initialised [SceneryWindow].
     */
    override fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow {
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)

        if (undecorated) {
            glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)
        }

        window = SceneryWindow.GLFWWindow(glfwCreateWindow(win.width, win.height, "scenery", MemoryUtil.NULL, MemoryUtil.NULL)).apply {
            width = win.width
            height = win.height

            glfwSetWindowPos(window, 100, 100)

            surface = VkSurface(VU.getLong("glfwCreateWindowSurface",
                { GLFWVulkan.glfwCreateWindowSurface(device.instance, window, null, this) }, {}))

            // Handle canvas resize
            windowSizeCallback = object : GLFWWindowSizeCallback() {
                override operator fun invoke(glfwWindow: Long, w: Int, h: Int) {
                    if (lastResize > 0L && lastResize + WINDOW_RESIZE_TIMEOUT < System.nanoTime()) {
                        lastResize = System.nanoTime()
                        return
                    }

                    if (width <= 0 || height <= 0)
                        return

                    width = w
                    height = h

                    swapchainRecreator.mustRecreate = true
                    lastResize = -1L
                }
            }

            glfwSetWindowSizeCallback(window, windowSizeCallback)
            glfwShowWindow(window)
        }

        return window
    }

    /**
     * Finds the best supported presentation mode, given the supported modes in [presentModes].
     * The preferred mode can be selected via [preferredMode]. Returns the preferred mode, or
     * VK_PRESENT_MODE_FIFO, if the preferred one is not supported.
     */
    private fun findBestPresentMode(presentModes: ArrayList<VkPresentMode>, preferredMode: VkPresentMode): VkPresentMode {
        return if (preferredMode in presentModes) {
            preferredMode
        } else {
            VkPresentMode.FIFO_KHR
        }
    }

    /**
     * Creates a new swapchain and returns it, potentially recycling or deallocating [oldSwapchain].
     */
    override fun create(oldSwapchain: Swapchain?): Swapchain {
        presentedFrames = 0

        val colorFormatAndSpace = getColorFormatAndSpace()
        val oldHandle = oldSwapchain?.handle

        // Get physical device surface properties and formats
        val surfCaps = phDev getSurfaceCapabilitiesKHR surface

        val presentModes = phDev getSurfacePresentModesKHR surface

        // use fifo mode (aka, vsynced) if requested,
        // otherwise, use mailbox mode and present the most recently generated frame.
        val preferredSwapchainPresentMode = when {
            vsync -> VkPresentMode.FIFO_KHR
            else -> VkPresentMode.IMMEDIATE_KHR
        }

        val swapchainPresentMode = findBestPresentMode(presentModes, preferredSwapchainPresentMode)

        // Determine the number of images
        var desiredNumberOfSwapchainImages = surfCaps.minImageCount + 1
        if (surfCaps.maxImageCount in 1 until desiredNumberOfSwapchainImages) {
            desiredNumberOfSwapchainImages = surfCaps.maxImageCount
        }

        val currentWidth = surfCaps.currentExtent.width
        val currentHeight = surfCaps.currentExtent.height

        if (currentWidth > 0 && currentHeight > 0) {
            window.width = currentWidth
            window.height = currentHeight
        } else {
            // TODO: Better default values
            window.width = 1920
            window.height = 1200
        }

        val preTransform = if (surfCaps.supportedTransforms has VkSurfaceTransform.IDENTITY_BIT_KHR) {
            VkSurfaceTransform.IDENTITY_BIT_KHR
        } else {
            surfCaps.currentTransform
        }

        val swapchainCI = vk.SwapchainCreateInfoKHR {
            surface = this@VulkanSwapchain.surface
            minImageCount = desiredNumberOfSwapchainImages
            imageFormat = colorFormatAndSpace.colorFormat
            imageColorSpace = colorFormatAndSpace.colorSpace
            imageUsage = VkImageUsage.COLOR_ATTACHMENT_BIT or VkImageUsage.TRANSFER_SRC_BIT
            this.preTransform = preTransform
            imageArrayLayers = 1
            imageSharingMode = VkSharingMode.EXCLUSIVE
            presentMode = swapchainPresentMode
            clipped = true
            compositeAlpha = VkCompositeAlpha.OPAQUE_BIT_KHR
            if (oldSwapchain is VulkanSwapchain || oldSwapchain is FXSwapchain) {
                oldHandle?.let { this.oldSwapchain = oldHandle }
            }
            imageExtent.width(window.width).height(window.height)
        }
        handle = vkDev createSwapchainKHR swapchainCI

        // If we just re-created an existing swapchain, we should destroy the old swapchain at this point.
        // Note: destroying the swapchain also cleans up all its associated presentable images once the platform is done with them.
        if (oldSwapchain is VulkanSwapchain && oldHandle?.isValid == true) {
            // TODO: Figure out why deleting a retired swapchain crashes on Nvidia
//                KHRSwapchain.vkDestroySwapchainKHR(device.vulkanDevice, oldHandle, null)
            retiredSwapchains += device to oldHandle
        }

        val swapchainImages = vkDev getSwapchainImagesKHR handle

        val images = VkImageArray(swapchainImages.size)
        val imageViews = VkImageViewArray(swapchainImages.size)
        val colorAttachmentView = vk.ImageViewCreateInfo {
            format = colorFormatAndSpace.colorFormat
            viewType = VkImageViewType.`2D`
            components.apply {
                r = VkComponentSwizzle.R
                g = VkComponentSwizzle.G
                b = VkComponentSwizzle.B
                a = VkComponentSwizzle.A
            }
            subresourceRange.apply {
                aspectMask = VkImageAspect.COLOR_BIT.i
                baseMipLevel = 0
                levelCount = 1
                baseArrayLayer = 0
                layerCount = 1
            }
        }

        vkDev.newCommandBuffer(commandPools.Standard)
            .record {
                for (i in swapchainImages.indices) {
                    images[i] = swapchainImages[i]

                    setImageLayout(images[i],
                        aspectMask = VkImageAspect.COLOR_BIT.i,
                        oldImageLayout = VkImageLayout.UNDEFINED,
                        newImageLayout = VkImageLayout.PRESENT_SRC_KHR)
                    colorAttachmentView.image = images[i]

                    imageViews[i] = vkDev createImageView colorAttachmentView
                }
            }
            .submit(queue)
            .deallocate()

        this.images = images
        this.imageViews = imageViews
        this.format = colorFormatAndSpace.colorFormat

        return this
    }

    /**
     * Returns the [ColorFormatAndSpace] supported by the [device].
     */
    protected fun getColorFormatAndSpace(): ColorFormatAndSpace {

        val queueProps = phDev.queueFamilyProperties

        // Iterate over each queue to learn whether it supports presenting:
        val supportsPresent = phDev.getSurfaceSupportKHR(queueProps, surface)

        // Search for a graphics and a present queue in the array of queue families, try to find one that supports both
        var graphicsQueueNodeIndex = Integer.MAX_VALUE
        var presentQueueNodeIndex = Integer.MAX_VALUE

        for (i in queueProps.indices) {
            if (queueProps[i].queueFlags has VkQueueFlag.GRAPHICS_BIT) {
                if (graphicsQueueNodeIndex == Integer.MAX_VALUE) {
                    graphicsQueueNodeIndex = i
                }
                if (supportsPresent[i]) {
                    graphicsQueueNodeIndex = i
                    presentQueueNodeIndex = i
                    break
                }
            }
        }

        if (presentQueueNodeIndex == Integer.MAX_VALUE) {
            // If there's no queue that supports both present and graphics try to find a separate present queue
            for (i in queueProps.indices) {
                if (supportsPresent[i]) {
                    presentQueueNodeIndex = i
                    break
                }
            }
        }

        // Generate error if could not find both a graphics and a present queue
        if (graphicsQueueNodeIndex == Integer.MAX_VALUE) {
            throw RuntimeException("No graphics queue found")
        }
        if (presentQueueNodeIndex == Integer.MAX_VALUE) {
            throw RuntimeException("No presentation queue found")
        }
        if (graphicsQueueNodeIndex != presentQueueNodeIndex) {
            throw RuntimeException("Presentation queue != graphics queue")
        }

        presentQueue = vkDev.getQueue(presentQueueNodeIndex, 0)

        // Get list of supported formats
        val surfFormats = phDev getSurfaceFormatsKHR surface

        val colorFormat = if (surfFormats.size == 1 && surfFormats[0].format == VkFormat.UNDEFINED) {
            if (useSRGB) {
                VkFormat.B8G8R8A8_SRGB
            } else {
                VkFormat.B8G8R8A8_UNORM
            }
        } else {
            if (useSRGB) {
                VkFormat.B8G8R8A8_SRGB
            } else {
                VkFormat.B8G8R8A8_UNORM
            }
        }

        val colorSpace = if (useSRGB) {
            VkColorSpace.SRGB_NONLINEAR_KHR
        } else {
            surfFormats[0].colorSpace
        }

        return ColorFormatAndSpace(colorFormat, colorSpace)
    }

    /**
     * Presents the current swapchain image on screen.
     */
    override fun present(waitForSemaphores: VkSemaphoreBuffer?) {
        // Present the current buffer to the swap chain
        // This will display the image
        swapchainPointer[0] = handle.L

        // Info struct to present the current swapchain image to the display
        presentInfo.apply {
            type = VkStructureType.PRESENT_INFO_KHR
            next = NULL
            swapchainCount = swapchainPointer.rem
            swapchains = swapchainPointer
            imageIndex = swapchainImage
            results = null
        }
        waitForSemaphores?.let { presentInfo.waitSemaphores = it }

        // here we accept the VK_ERROR_OUT_OF_DATE_KHR error code, which
        // seems to spuriously occur on Linux upon resizing.
        presentQueue.presentKHR(presentInfo) {
            if (it != SUCCESS && it != ERROR_OUT_OF_DATE_KHR)
                throw Error()
        }

        presentedFrames++
    }

    /**
     * To be called after presenting, will deallocate retired swapchains.
     */
    override fun postPresent(image: Int) {
        while (retiredSwapchains.isNotEmpty()) {
            retiredSwapchains.poll()?.let {
                it.first.vulkanDevice destroySwapchainKHR it.second
            }
        }
    }

    /**
     * Acquires the next swapchain image.
     */
    override fun next(timeout: Long, signalSemaphore: VkSemaphore): Boolean {
        // wait for the present queue to become idle - by doing this here
        // we avoid stalling the GPU and gain a few FPS
        presentQueue.waitIdle()

        var result = false
        swapchainImage = vkDev.acquireNextImageKHR(handle, timeout, signalSemaphore) {
            if (it == ERROR_OUT_OF_DATE_KHR || it == SUBOPTIMAL_KHR) {
                result = true
            } else if (it != SUCCESS) {
                throw AssertionError("Failed to acquire next swapchain image: ${it.description}")
            }
        }
        return result
    }

    /**
     * Changes the current window to fullscreen.
     */
    override fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator) {
        (window as SceneryWindow.GLFWWindow?)?.let { window ->
            if (window.isFullscreen) {
                glfwSetWindowMonitor(window.window,
                    MemoryUtil.NULL,
                    0, 0,
                    window.width, window.height, GLFW_DONT_CARE)
                glfwSetWindowPos(window.window, 100, 100)
                glfwSetInputMode(window.window, GLFW_CURSOR, GLFW_CURSOR_NORMAL)

                swapchainRecreator.mustRecreate = true
                window.isFullscreen = false
            } else {
                val preferredMonitor = System.getProperty("scenery.FullscreenMonitor", "0").toInt()

                val monitor = if (preferredMonitor == 0) {
                    glfwGetPrimaryMonitor()
                } else {
                    val monitors = glfwGetMonitors()
                    if (monitors != null && monitors.remaining() >= preferredMonitor) {
                        monitors.get(preferredMonitor)
                    } else {
                        glfwGetPrimaryMonitor()
                    }
                }

                val hmd = hub.getWorkingHMDDisplay()

                if (hmd != null) {
                    window.width = hmd.getRenderTargetSize().x().toInt() / 2
                    window.height = hmd.getRenderTargetSize().y().toInt()
                    logger.info("Set fullscreen window dimensions to ${window.width}x${window.height}")
                }

                glfwSetWindowMonitor(window.window,
                    monitor,
                    0, 0,
                    window.width, window.height, GLFW_DONT_CARE)
                glfwSetInputMode(window.window, GLFW_CURSOR, GLFW_CURSOR_HIDDEN)

                swapchainRecreator.mustRecreate = true
                window.isFullscreen = true
            }
        }
    }

    /**
     * Embeds the swapchain into a [SceneryPanel]. Not supported by [VulkanSwapchain],
     * see [FXSwapchain] instead.
     */
    override fun embedIn(panel: SceneryPanel?) {
        if (panel == null) {
            return
        }

        logger.error("Embedding is not supported with the default Vulkan swapchain. Use FXSwapchain instead.")
    }

    /**
     * Returns the number of fully presented frames.
     */
    override fun presentedFrames(): Long {
        return presentedFrames
    }

    /**
     * Closes the swapchain, deallocating all of its resources.
     */
    override fun close() {
        logger.debug("Closing swapchain $this")
        KHRSwapchain.vkDestroySwapchainKHR(device.vulkanDevice, handle.L, null)

        presentInfo.free()
        swapchainPointer.free()

        windowSizeCallback.close()
        (window as SceneryWindow.GLFWWindow?)?.let { window ->
            glfwDestroyWindow(window.window)
            glfwTerminate()
        }
    }
}
