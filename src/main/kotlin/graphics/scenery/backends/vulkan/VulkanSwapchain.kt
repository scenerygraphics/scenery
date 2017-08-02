package graphics.scenery.backends.vulkan

import graphics.scenery.Hub
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.SceneryPanel
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR
import java.nio.IntBuffer
import java.nio.LongBuffer

/**
 * GLFW-based default Vulkan Swapchain and window.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanSwapchain(open val device: VkDevice,
                           open val physicalDevice: VkPhysicalDevice,
                           open val instance: VkInstance,
                           open val queue: VkQueue,
                           open val commandPool: Long,
                           @Suppress("unused") open val renderConfig: RenderConfigReader.RenderConfig,
                           open val useSRGB: Boolean = true) : Swapchain {
    protected val logger by LazyLogger()

    override var handle: Long = 0L
    override var images: LongArray? = null
    override var imageViews: LongArray? = null

    override var format: Int = 0

    var swapchainImage: IntBuffer = MemoryUtil.memAllocInt(1)
    var swapchainPointer: LongBuffer = MemoryUtil.memAllocLong(1)
    var presentInfo: VkPresentInfoKHR = VkPresentInfoKHR.calloc()
    open var surface: Long = 0
    lateinit var window: SceneryWindow
    lateinit var windowSizeCallback: GLFWWindowSizeCallback

    var lastResize = -1L
    private val WINDOW_RESIZE_TIMEOUT = 200 * 10e6

    data class ColorFormatAndSpace(var colorFormat: Int = 0, var colorSpace: Int = 0)

    override fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow {
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)

        window = SceneryWindow.GLFWWindow(glfwCreateWindow(win.width, win.height, "scenery", MemoryUtil.NULL, MemoryUtil.NULL)).apply {
            width = win.width
            height = win.height

            glfwSetWindowPos(window, 100, 100)

            surface = VU.run(MemoryUtil.memAllocLong(1), "glfwCreateWindowSurface") {
                GLFWVulkan.glfwCreateWindowSurface(instance, window, null, this)
            }

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

    override fun create(oldSwapchain: Swapchain?): Swapchain {
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

        if (oldSwapchain is VulkanSwapchain || oldSwapchain is FXSwapchain) {
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

        this.images = images
        this.imageViews = imageViews
        this.handle = swapChain
        this.format = colorFormatAndSpace.colorFormat

        return this
    }

    private fun getColorFormatAndSpace(): ColorFormatAndSpace {
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

        return ColorFormatAndSpace(colorFormat, colorSpace)
    }

    override fun present(waitForSemaphores: LongBuffer?) {
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
    }

    override fun next(timeout: Long, waitForSemaphore: Long): Boolean {
        val err = vkAcquireNextImageKHR(device, handle, timeout,
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
                    if (monitors.remaining() < preferredMonitor) {
                        monitors.get(0)
                    } else {
                        monitors.get(preferredMonitor)
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

    override fun embedIn(panel: SceneryPanel?) {
        if(panel == null) {
            return
        }

        logger.error("Embedding is not supported with the default Vulkan swapchain. Use FXSwapchain instead.")
    }

    override fun close() {
        KHRSwapchain.vkDestroySwapchainKHR(device, handle, null)

        presentInfo.free()
        MemoryUtil.memFree(swapchainImage)
        MemoryUtil.memFree(swapchainPointer)

        windowSizeCallback.close()
        (window as SceneryWindow.GLFWWindow?)?.let { window -> glfwDestroyWindow(window.window) }
    }
}
