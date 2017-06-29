package graphics.scenery.backends.vulkan

import com.jogamp.opengl.GL2ES3.GL_MAJOR_VERSION
import com.jogamp.opengl.GL2ES3.GL_MINOR_VERSION
import com.sun.jna.PointerUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWNativeWin32.*
import org.lwjgl.glfw.GLFWNativeGLX.*
import org.lwjgl.glfw.GLFWNativeX11.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.opengl.NVDrawVulkanImage
import org.lwjgl.opengl.GLXNVSwapGroup
import org.lwjgl.opengl.WGLNVSwapGroup
import org.lwjgl.system.Platform
import org.slf4j.LoggerFactory
import java.nio.LongBuffer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import java.lang.UnsupportedOperationException

/**
 * GLFW-based OpenGL swapchain and window, using Nvidia's NV_draw_vulkan_image GL extension.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class OpenGLSwapchain(val window: SceneryWindow,
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

    val logger = LoggerFactory.getLogger("VulkanRenderer")
    val supportedExtensions = ArrayList<String>()

    var surface: Long = 0
    lateinit var windowSizeCallback: GLFWWindowSizeCallback

    var lastResize = -1L
    private val WINDOW_RESIZE_TIMEOUT = 200 * 10e6

    override fun createWindow(window: SceneryWindow, instance: VkInstance, swapchainRecreator: VulkanRenderer.SwapchainRecreator) {
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

        surface = VU.run(MemoryUtil.memAllocLong(1), "glfwCreateWindowSurface") {
            GLFWVulkan.glfwCreateWindowSurface(instance, window.glfwWindow!!, null, this)
        }

        // Handle canvas resize
        windowSizeCallback = object : GLFWWindowSizeCallback() {
            override operator fun invoke(glfwWindow: Long, w: Int, h: Int) {
                if (lastResize > 0L && lastResize + WINDOW_RESIZE_TIMEOUT < System.nanoTime()) {
                    lastResize = System.nanoTime()
                    return
                }

                if (window.width <= 0 || window.height <= 0)
                    return

                window.width = w
                window.height = h
                swapchainRecreator.mustRecreate = true
                lastResize = -1L
            }
        }

        glfwSetWindowSizeCallback(window.glfwWindow!!, windowSizeCallback)
        glfwShowWindow(window.glfwWindow!!)
    }

    override fun create(oldSwapchain: Swapchain?): Swapchain {
        glfwMakeContextCurrent(window.glfwWindow!!)
        GL.createCapabilities()

        logger.info("OpenGL swapchain running OpenGL ${glGetInteger(GL_MAJOR_VERSION)}.${glGetInteger(GL_MINOR_VERSION)} on ${glGetString(GL_RENDERER)}")

        (0..glGetInteger(GL_NUM_EXTENSIONS) - 1).map {
            supportedExtensions.add(glGetStringi(GL_EXTENSIONS, it))
        }

        if (!supportedExtensions.contains("GL_NV_draw_vulkan_image")) {
            logger.error("NV_draw_vulkan_image not supported. Please use standard Vulkan swapchain.")
            throw UnsupportedOperationException("NV_draw_vulkan_image not supported. Please use standard Vulkan swapchain.")
        }

        format = if (useSRGB) {
            VK10.VK_FORMAT_B8G8R8A8_SRGB
        } else {
            VK10.VK_FORMAT_B8G8R8A8_UNORM
        }

        if (window.width <= 0 || window.height <= 0) {
            logger.warn("Received invalid window dimensions, resizing to sane values")
            // TODO: Better default values
            window.width = 1920
            window.height = 1200
        }

        val imgs = (0..bufferCount - 1).map {
            with(VU.newCommandBuffer(device, commandPool, autostart = true)) {
                val t = VulkanTexture(device, physicalDevice, memoryProperties, commandPool, queue,
                    window.width, window.height, 1, format, 1)

                val image = t.createImage(window.width, window.height, 1,
                    format, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT,
                    VK_IMAGE_TILING_OPTIMAL, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    1)

                VulkanTexture.transitionLayout(image.image,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 1,
                    commandBuffer = this)

                val view = t.createImageView(image, format)

                this.endCommandBuffer(device, commandPool, queue, flush = true, dealloc = true)
                Pair(image.image, view)
            }
        }

        images = imgs.map { it.first }.toLongArray()
        imageViews = imgs.map { it.second }.toLongArray()

        handle = -1L

        glfwSwapInterval(0)
        glfwShowWindow(window.glfwWindow!!)

        glEnable(GL_FRAMEBUFFER_SRGB)

        if (useFramelock) {
            enableFramelock()
        }

        return this
    }

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
                val hwnd = glfwGetWin32Window(window.glfwWindow!!)
                val hwndP = WinDef.HWND(PointerUtils.fromAddress(hwnd))
                val hdc = User32.INSTANCE.GetDC(hwndP)

                WGLNVSwapGroup.wglQueryMaxSwapGroupsNV(PointerUtils.getAddress(hdc), maxGroups, maxBarriers)

                if (!WGLNVSwapGroup.wglJoinSwapGroupNV(PointerUtils.getAddress(hdc), swapGroup)) {
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
                val window = glfwGetGLXWindow(window.glfwWindow!!)

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

    fun disableFramelock() {
        if (!supportedExtensions.contains("WGL_NV_swap_group")) {
            logger.warn("Framelock requested, but not supported on this hardware.")
            return
        }

        when (Platform.get()) {
            Platform.WINDOWS -> {
                val hwnd = glfwGetWin32Window(window.glfwWindow!!)
                val hwndP = WinDef.HWND(PointerUtils.fromAddress(hwnd))
                val hdc = User32.INSTANCE.GetDC(hwndP)

                WGLNVSwapGroup.wglJoinSwapGroupNV(PointerUtils.getAddress(hdc), 0)
            }

            Platform.LINUX -> {
                val display = glfwGetX11Display()
                val window = glfwGetGLXWindow(window.glfwWindow!!)

                GLXNVSwapGroup.glXJoinSwapGroupNV(display, window, 0)
            }

            else -> logger.error("Hardware Framelock not supported on this platform.")
        }

    }

    override fun present(waitForSemaphores: LongBuffer?) {
        glDisable(GL_DEPTH_TEST)

        waitForSemaphores?.let { NVDrawVulkanImage.glWaitVkSemaphoreNV(waitForSemaphores.get(0)) }

        // note: glDrawVkImageNV expects the OpenGL screen space conventions,
        // so the Vulkan image's ST coordinates have to be flipped
        if (renderConfig.stereoEnabled) {
            glDrawBuffer(GL_BACK_LEFT)
            glClear(GL_COLOR_BUFFER_BIT)
            glDisable(GL_DEPTH_TEST)

            NVDrawVulkanImage.glDrawVkImageNV(images!!.get(0), 0,
                0.0f, 0.0f, window.width.toFloat(), window.height.toFloat(), 0.0f,
                0.0f, 1.0f, 0.5f, 0.0f)

            glDrawBuffer(GL_BACK_RIGHT)
            glClear(GL_COLOR_BUFFER_BIT)
            glDisable(GL_DEPTH_TEST)

            NVDrawVulkanImage.glDrawVkImageNV(images!!.get(0), 0,
                0.0f, 0.0f, window.width.toFloat(), window.height.toFloat(), 0.0f,
                0.5f, 1.0f, 1.0f, 0.0f)
        } else {
            glClear(GL_COLOR_BUFFER_BIT)
            NVDrawVulkanImage.glDrawVkImageNV(images!!.get(0), 0,
                0.0f, 0.0f, window.width.toFloat(), window.height.toFloat(), 0.0f,
                0.0f, 1.0f, 1.0f, 0.0f)
        }

        glfwSwapBuffers(window.glfwWindow!!)
    }

    override fun next(timeout: Long, waitForSemaphore: Long): Boolean {
        NVDrawVulkanImage.glSignalVkSemaphoreNV(waitForSemaphore)
        return false
    }

    override fun close() {
        imageViews?.forEach { vkDestroyImageView(device, it, null) }
        images?.forEach { vkDestroyImage(device, it, null) }

        windowSizeCallback.close()
        glfwDestroyWindow(window.glfwWindow!!)
    }
}
