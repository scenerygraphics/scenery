package graphics.scenery.backends.vulkan

import com.jogamp.opengl.GL2ES3.GL_MAJOR_VERSION
import com.jogamp.opengl.GL2ES3.GL_MINOR_VERSION
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import graphics.scenery.Hub
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.SceneryPanel
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWNativeGLX.glfwGetGLXWindow
import org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window
import org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GLXNVSwapGroup
import org.lwjgl.opengl.NVDrawVulkanImage
import org.lwjgl.opengl.WGLNVSwapGroup
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memAllocInt
import org.lwjgl.system.Platform
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkQueue
import uno.glfw.glfw
import vkk.VkFormat
import vkk.VkImageLayout
import vkk.VkImageTiling
import vkk.`object`.*
import java.nio.LongBuffer

/**
 * GLFW-based OpenGL swapchain and window, using Nvidia's NV_draw_vulkan_image GL extension.
 * The swapchain will reside on [device] and submit to [queue]. All other parameters are not used.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class OpenGLSwapchain(val device: VulkanDevice,
                      val queue: VkQueue,
                      val commandPools: VulkanRenderer.CommandPools,
                      val renderConfig: RenderConfigReader.RenderConfig,
                      val useSRGB: Boolean = true,
                      val useFramelock: Boolean = false,
                      val bufferCount: Int = 2) : Swapchain {
    private val logger by LazyLogger()

    /** Swapchain handle. */
    override var handle = VkSwapchainKHR(NULL)
    /** Array for rendered images. */
    override var images = VkImageArray()
    /** Array for image views. */
    override var imageViews = VkImageViewArray()
    /** Number of frames presented with this swapchain. */
    protected var presentedFrames = 0L

    /** Color format of the images. */
    override var format = VkFormat.UNDEFINED

    /** Window instance to use. */
    lateinit var window: SceneryWindow.GLFWWindow

    /** List of supported OpenGL extensions. */
    val supportedExtensions = ArrayList<String>()

    /** Window surface to use. */
    var surface: Long = 0
    /** Window size callback to use. */
    lateinit var windowSizeCallback: GLFWWindowSizeCallback

    /** Time of the last resize event in ns. */
    var lastResize = -1L
    private val WINDOW_RESIZE_TIMEOUT = 200 * 10e6

    /**
     * Creates a window for this swapchain, and initialiases [win] as [SceneryWindow.GLFWWindow].
     * Needs to be handed a [VulkanRenderer.SwapchainRecreator].
     * Returns the initialised [SceneryWindow].
     */
    override fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow {
        glfwDefaultWindowHints()

        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5)
        glfwWindowHint(GLFW_SRGB_CAPABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)

        glfwWindowHint(GLFW_STEREO, if (renderConfig.stereoEnabled) {
            GLFW_TRUE
        } else {
            GLFW_FALSE
        })

        window = SceneryWindow.GLFWWindow(glfwCreateWindow(win.width, win.height, "scenery", MemoryUtil.NULL, MemoryUtil.NULL)).apply {
            glfwSetWindowPos(window, 100, 100)

            surface = VU.getLong("glfwCreateWindowSurface",
                { GLFWVulkan.glfwCreateWindowSurface(device.instance, window, null, this) }, {})

            // Handle canvas resize
            windowSizeCallback = object : GLFWWindowSizeCallback() {
                override operator fun invoke(window: Long, w: Int, h: Int) {
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
     * Creates a new swapchain and returns it, potentially recycling or deallocating [oldSwapchain].
     * In the special case of the [OpenGLSwapchain], an OpenGL context is created and checked for the
     * GL_NV_draw_vulkan_image extension, which it requires.
     */
    override fun create(oldSwapchain: Swapchain?): Swapchain {
        presentedFrames = 0
        glfwMakeContextCurrent(window.window)
        GL.createCapabilities()

        logger.info("OpenGL swapchain running OpenGL ${glGetInteger(GL_MAJOR_VERSION)}.${glGetInteger(GL_MINOR_VERSION)} on ${glGetString(GL_RENDERER)}")

        (0 until glGetInteger(GL_NUM_EXTENSIONS)).map {
            supportedExtensions.add(glGetStringi(GL_EXTENSIONS, it) ?: "")
        }

        if (!supportedExtensions.contains("GL_NV_draw_vulkan_image")) {
            logger.error("NV_draw_vulkan_image not supported. Please use standard Vulkan swapchain.")
            throw UnsupportedOperationException("NV_draw_vulkan_image not supported. Please use standard Vulkan swapchain.")
        }

        format = if (useSRGB) {
            VkFormat.B8G8R8A8_SRGB
        } else {
            VkFormat.B8G8R8A8_UNORM
        }

        if (window.width <= 0 || window.height <= 0) {
            logger.warn("Received invalid window dimensions, resizing to sane values")
            // TODO: Better default values
            window.width = 1920
            window.height = 1200
        }

        images = VkImageArray(bufferCount)
        imageViews = VkImageViewArray(bufferCount)

        for (i in 0 until bufferCount)
            device.vulkanDevice.newCommandBuffer(commandPools.Standard)
                .record {
                    val t = VulkanTexture(this@OpenGLSwapchain.device, commandPools, queue, queue,
                        window.width, window.height, 1, format, 1)

                    val image = t.createImage(window.width, window.height, 1,
                        format, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT,
                        VkImageTiling.OPTIMAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                        1)

                    VulkanTexture.transitionLayout(image.image,
                        VkImageLayout.UNDEFINED,
                        VkImageLayout.SHADER_READ_ONLY_OPTIMAL, 1,
                        commandBuffer = this)

                    images[i] = image.image
                    imageViews[i] = t.createImageView(image, format)
                }
                .submit(queue).deallocate()

        handle = VkSwapchainKHR(NULL)

        glfw.swapInterval = 0
        glfwShowWindow(window.window)

        glEnable(GL_FRAMEBUFFER_SRGB)

        if (useFramelock) {
            enableFramelock()
        }

        return this
    }

    /**
     * Enables frame-locking for this swapchain, locking buffer swap events to other screens/machines.
     * Works only if the WGL_NV_swap_group (Windows) or GLX_NV_swap_group extension is supported.
     */
    fun enableFramelock(): Boolean {
        if (!supportedExtensions.contains("WGL_NV_swap_group") && !supportedExtensions.contains("GLX_NV_swap_group")) {
            logger.warn("Framelock requested, but not supported on this hardware.")
            // TODO: Figure out why K6000 does not report WGL_NV_swap_group correctly.
            // return false
        }

        val swapGroup = System.getProperty("scenery.VulkanRenderer.SwapGroup", "1").toInt()
        val swapBarrier = System.getProperty("scenery.VulkanRenderer.SwapBarrier", "1").toInt()

        val maxGroups = memAllocInt(1)
        val maxBarriers = memAllocInt(1)

        when (Platform.get()) {
            Platform.WINDOWS -> {
                val hwnd = glfwGetWin32Window(window.window)
                val hwndP = WinDef.HWND(Pointer(hwnd))
                val hdc = User32.INSTANCE.GetDC(hwndP)

                WGLNVSwapGroup.wglQueryMaxSwapGroupsNV(Pointer.nativeValue(hdc.pointer), maxGroups, maxBarriers)

                if (!WGLNVSwapGroup.wglJoinSwapGroupNV(Pointer.nativeValue(hdc.pointer), swapGroup)) {
                    logger.error("Failed to bind to swap group $swapGroup")
                    return false
                }

                if (!WGLNVSwapGroup.wglBindSwapBarrierNV(swapGroup, swapBarrier)) {
                    logger.error("Failed to bind to swap barrier $swapBarrier on swap group $swapGroup")
                    return false
                }

                logger.info("Joined swap barrier $swapBarrier on swap group $swapGroup")
                return true
            }

            Platform.LINUX -> {
                val display = glfwGetX11Display()
                val window = glfwGetGLXWindow(window.window)

                GLXNVSwapGroup.glXQueryMaxSwapGroupsNV(display, 0, maxGroups, maxBarriers)

                if (GLXNVSwapGroup.glXJoinSwapGroupNV(display, window, swapGroup)) {
                    logger.error("Failed to bind to swap group $swapGroup")
                    return false
                }

                if (GLXNVSwapGroup.glXBindSwapBarrierNV(display, swapGroup, swapBarrier)) {
                    logger.error("Failed to bind to swap barrier $swapBarrier on swap group $swapGroup")
                    return false
                }

                logger.info("Joined swap barrier $swapBarrier on swap group $swapGroup")
                return true
            }

            else -> {
                logger.warn("Hardware Framelock not supported on this platform.")
                return false
            }
        }
    }

    /**
     * Disables frame-lock for this swapchain.
     */
    @Suppress("unused")
    fun disableFramelock() {
        if (!supportedExtensions.contains("WGL_NV_swap_group")) {
            logger.warn("Framelock requested, but not supported on this hardware.")
            return
        }

        when (Platform.get()) {
            Platform.WINDOWS -> {
                val hwnd = glfwGetWin32Window(window.window)
                val hwndP = WinDef.HWND(Pointer(hwnd))
                val hdc = User32.INSTANCE.GetDC(hwndP)

                WGLNVSwapGroup.wglJoinSwapGroupNV(Pointer.nativeValue(hdc.pointer), 0)
            }

            Platform.LINUX -> {
                val display = glfwGetX11Display()
                val window = glfwGetGLXWindow(window.window)

                GLXNVSwapGroup.glXJoinSwapGroupNV(display, window, 0)
            }

            else -> logger.error("Hardware Framelock not supported on this platform.")
        }

    }

    /**
     * Presents the currently rendered image, drawing the Vulkan image into the current
     * OpenGL context.
     */
    override fun present(waitForSemaphores: VkSemaphoreBuffer?) {
        glDisable(GL_DEPTH_TEST)

        waitForSemaphores?.let { NVDrawVulkanImage.glWaitVkSemaphoreNV(waitForSemaphores[0].L) }

        // note: glDrawVkImageNV expects the OpenGL screen space conventions,
        // so the Vulkan image's ST coordinates have to be flipped
        if (renderConfig.stereoEnabled) {
            glDrawBuffer(GL_BACK_LEFT)
            glClear(GL_COLOR_BUFFER_BIT)
            glDisable(GL_DEPTH_TEST)

            NVDrawVulkanImage.glDrawVkImageNV(images[0].L, 0,
                0.0f, 0.0f, window.width.toFloat(), window.height.toFloat(), 0.0f,
                0.0f, 1.0f, 0.5f, 0.0f)

            glDrawBuffer(GL_BACK_RIGHT)
            glClear(GL_COLOR_BUFFER_BIT)
            glDisable(GL_DEPTH_TEST)

            NVDrawVulkanImage.glDrawVkImageNV(images[0].L, 0,
                0.0f, 0.0f, window.width.toFloat(), window.height.toFloat(), 0.0f,
                0.5f, 1.0f, 1.0f, 0.0f)
        } else {
            glClear(GL_COLOR_BUFFER_BIT)
            NVDrawVulkanImage.glDrawVkImageNV(images[0].L, 0,
                0.0f, 0.0f, window.width.toFloat(), window.height.toFloat(), 0.0f,
                0.0f, 1.0f, 1.0f, 0.0f)
        }

        glfwSwapBuffers(window.window)
        presentedFrames++
    }

    /**
     * Post-present routine, does nothing in this case.
     */
    override fun postPresent(image: Int) {
    }

    /**
     * Proceeds to the next swapchain image.
     */
    override fun next(timeout: Long, signalSemaphore: VkSemaphore): Boolean {
        NVDrawVulkanImage.glSignalVkSemaphoreNV(signalSemaphore.L)
        return false
    }

    /**
     * Toggles fullscreen.
     */
    override fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator) {
        if (window.isFullscreen) {
            glfwSetWindowMonitor(window.window,
                NULL,
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

    /**
     * Embeds the swapchain into a [SceneryPanel]. Not supported by [OpenGLSwapchain], see [FXSwapchain] instead.
     */
    override fun embedIn(panel: SceneryPanel?) {
        logger.error("Embedding is not supported with the OpenGL-based swapchain. Use FXSwapchain instead.")
    }

    /**
     * Returns the number of presented frames.
     */
    override fun presentedFrames(): Long {
        return presentedFrames
    }

    /**
     * Closes the swapchain, freeing all of its resources.
     */
    override fun close() {
        imageViews.forEach { vkDestroyImageView(device.vulkanDevice, it.L, null) }
        images.forEach { vkDestroyImage(device.vulkanDevice, it.L, null) }

        windowSizeCallback.close()
        glfwDestroyWindow(window.window)
    }
}
