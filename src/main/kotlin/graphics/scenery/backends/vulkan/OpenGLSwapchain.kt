package graphics.scenery.backends.vulkan

import com.jogamp.opengl.GL2ES3.GL_MAJOR_VERSION
import com.jogamp.opengl.GL2ES3.GL_MINOR_VERSION
import com.sun.jna.PointerUtils
import graphics.scenery.backends.SceneryWindow
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWNativeWin32.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.opengl.NVDrawVulkanImage
import org.lwjgl.opengl.GLXNVSwapGroup
import org.lwjgl.opengl.WGLNVSwapGroup
import org.slf4j.LoggerFactory
import java.nio.LongBuffer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import org.lwjgl.opengl.GL30.GL_NUM_EXTENSIONS
import org.lwjgl.opengl.GL30.glGetStringi
import org.lwjgl.system.MemoryUtil.memAllocInt

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class OpenGLSwapchain(val window: SceneryWindow,
                      val device: VkDevice,
                      val physicalDevice: VkPhysicalDevice,
                      val memoryProperties: VkPhysicalDeviceMemoryProperties,
                      val queue: VkQueue,
                      val commandPool: Long,
                      val surface: Long,
                      val useSRGB: Boolean = true,
                      val useFramelock: Boolean = false,
                      val bufferCount: Int = 2) : Swapchain {

    override var handle: Long = 0L
    override var images: LongArray? = null
    override var imageViews: LongArray? = null

    override var format: Int = 0

    val logger = LoggerFactory.getLogger("VulkanRenderer")
    val supportedExtensions = ArrayList<String>()

    override fun create(oldSwapchain: Swapchain?): Swapchain {
        glfwMakeContextCurrent(window.glfwWindow!!)
        GL.createCapabilities()

        logger.info("OpenGL swapchain running on top of OpenGL ${glGetInteger(GL_MAJOR_VERSION)}.${glGetInteger(GL_MINOR_VERSION)}")

        (0..glGetInteger(GL_NUM_EXTENSIONS)).map {
            supportedExtensions.add(glGetStringi(GL_EXTENSIONS, it))
        }

        format = if (useSRGB) {
            VK10.VK_FORMAT_B8G8R8A8_SRGB
        } else {
            VK10.VK_FORMAT_B8G8R8A8_UNORM
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

        if(useFramelock) {
            enableFramelock()
        }

        return this
    }

    fun enableFramelock(): Boolean {
        if(!supportedExtensions.contains("WGL_NV_swap_group")) {
            logger.warn("Framelock requested, but not supported on this hardware.")
            return false
        }

        val hwnd = glfwGetWin32Window(window.glfwWindow!!)
        val hwndP = WinDef.HWND(PointerUtils.fromAddress(hwnd))
        val hdc = User32.INSTANCE.GetDC(hwndP)

        val maxGroups = memAllocInt(1)
        val maxBarriers = memAllocInt(1)

        val swapGroup = System.getProperty("scenery.VulkanRenderer.SwapGroup", "1").toInt()
        val swapBarrier = System.getProperty("scenery.VulkanRenderer.SwapBarrier", "1").toInt()

        WGLNVSwapGroup.wglQueryMaxSwapGroupsNV(PointerUtils.getAddress(hdc), maxGroups, maxBarriers)

        if(!WGLNVSwapGroup.wglJoinSwapGroupNV(PointerUtils.getAddress(hdc), swapGroup)) {
            logger.error("Failed to bind to swap group $swapGroup")
            return false
        }

        if(!WGLNVSwapGroup.wglBindSwapBarrierNV(swapGroup, swapBarrier)) {
            logger.error("Failed to bind to swap barrier $swapBarrier on swap group $swapGroup")
            return false
        }

        logger.info("Joined swap barrier 1 on swap group 1")
        return true
    }

    fun disableFramelock() {
        if(!supportedExtensions.contains("WGL_NV_swap_group")) {
            logger.warn("Framelock requested, but not supported on this hardware.")
            return
        }

        val hwnd = glfwGetWin32Window(window.glfwWindow!!)
        val hwndP = WinDef.HWND(PointerUtils.fromAddress(hwnd))
        val hdc = User32.INSTANCE.GetDC(hwndP)

        WGLNVSwapGroup.wglJoinSwapGroupNV(PointerUtils.getAddress(hdc), 0)
    }

    override fun present(waitForSemaphores: LongBuffer?) {
        glDisable(GL_DEPTH_TEST)
        glClear(GL_COLOR_BUFFER_BIT)

        waitForSemaphores?.let { NVDrawVulkanImage.glWaitVkSemaphoreNV(waitForSemaphores.get(0)) }
        NVDrawVulkanImage.glDrawVkImageNV(images!!.get(0), 0, 0.0f, 0.0f, window.width.toFloat(), window.height.toFloat(), 0.0f, 0.0f, 1.0f, 1.0f, 0.0f)

        glfwSwapBuffers(window.glfwWindow!!)
    }

    override fun next(timeout: Long, waitForSemaphore: Long): Boolean {
        NVDrawVulkanImage.glSignalVkSemaphoreNV(waitForSemaphore)
        return false
    }

    override fun close() {
        imageViews?.forEach { vkDestroyImageView(device, it, null) }
        images?.forEach { vkDestroyImage(device, it, null) }
    }
}
