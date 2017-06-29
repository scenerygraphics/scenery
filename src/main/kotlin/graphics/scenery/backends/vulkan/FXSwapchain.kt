package graphics.scenery.backends.vulkan

import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.slf4j.LoggerFactory
import java.nio.LongBuffer

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

    private var glfwWindow: Long = 0

    var surface: Long = 0
    lateinit var windowSizeCallback: GLFWWindowSizeCallback

    var lastResize = -1L
    private val WINDOW_RESIZE_TIMEOUT = 200 * 10e6

    val logger = LoggerFactory.getLogger("FXSwapchain")

    override fun createWindow(window: SceneryWindow, instance: VkInstance, swapchainRecreator: VulkanRenderer.SwapchainRecreator) {
        // create off-screen GLFW window
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)

        window.glfwWindow = glfwCreateWindow(window.width, window.height, "scenery", MemoryUtil.NULL, MemoryUtil.NULL)

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
    }

    override fun create(oldSwapchain: Swapchain?): Swapchain {
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
                    format, VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                    VK10.VK_IMAGE_TILING_OPTIMAL, VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    1)

                VulkanTexture.transitionLayout(image.image,
                    VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                    VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 1,
                    commandBuffer = this)

                val view = t.createImageView(image, format)

                this.endCommandBuffer(device, commandPool, queue, flush = true, dealloc = true)
                Pair(image.image, view)
            }
        }

        images = imgs.map { it.first }.toLongArray()
        imageViews = imgs.map { it.second }.toLongArray()

        handle = -1L
    }

    override fun present(waitForSemaphores: LongBuffer?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun next(timeout: Long, waitForSemaphore: Long): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun close() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
