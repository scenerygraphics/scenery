package graphics.scenery.backends.vulkan

import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
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
import org.slf4j.Logger
import java.util.concurrent.locks.ReentrantLock


/**
 * Extended Vulkan swapchain compatible with JavaFX
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class FXSwapchain(device: VkDevice,
                  physicalDevice: VkPhysicalDevice,
                  instance: VkInstance,
                  val memoryProperties: VkPhysicalDeviceMemoryProperties,
                  queue: VkQueue,
                  commandPool: Long,
                  renderConfig: RenderConfigReader.RenderConfig,
                  useSRGB: Boolean = true,
                  @Suppress("unused") val useFramelock: Boolean = false,
                  @Suppress("unused") val bufferCount: Int = 2) : VulkanSwapchain(device, physicalDevice, instance, queue, commandPool, renderConfig, useSRGB) {
    lateinit var sharingBuffer: VulkanBuffer
    lateinit var imageBuffer: ByteBuffer
    var lock = ReentrantLock()

    private var glfwOffscreenWindow: Long = -1L
    lateinit var stage: Stage
    private var imagePanel: SceneryPanel? = null

    lateinit var vulkanInstance: VkInstance
    lateinit var vulkanSwapchainRecreator: VulkanRenderer.SwapchainRecreator

    private val WINDOW_RESIZE_TIMEOUT = 400 * 10e6

    override var logger: Logger = LoggerFactory.getLogger("FXSwapchain")

    inner class ResizeHandler {
        @Volatile var lastResize = -1L
        var lastWidth = 0
        var lastHeight = 0

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
        vulkanInstance = instance
        vulkanSwapchainRecreator = swapchainRecreator

        PlatformImpl.startup { }
        val lCountDownLatch = CountDownLatch(1)
        Platform.runLater {
            if (imagePanel == null) {
                stage = Stage()
                stage.title = "FXSwapchain"

                val lStackPane = StackPane()
                lStackPane.backgroundProperty()
                    .set(Background(BackgroundFill(Color.TRANSPARENT,
                        CornerRadii.EMPTY,
                        Insets.EMPTY)))

                val pane = GridPane()
                val label = Label("Experimental JavaFX Swapchain - use with caution!")

                imagePanel = SceneryPanel(win.width, win.height).apply {
                    window = SceneryWindow.JavaFXStage(this)
                }

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
            } else {
                imagePanel?.let {
                    window = SceneryWindow.JavaFXStage(it)

                    stage = it.scene.window as Stage
                }
            }

            window.width = win.width
            window.height = win.height

            resizeHandler.lastWidth = win.width
            resizeHandler.lastHeight = win.height

            imagePanel?.widthProperty()?.addListener { _, _, newWidth ->
                resizeHandler.lastWidth = newWidth.toInt()
            }

            imagePanel?.heightProperty()?.addListener { _, _, newHeight ->
                resizeHandler.lastHeight = newHeight.toInt()
            }

            imagePanel?.minWidth = 100.0
            imagePanel?.minHeight = 100.0
            imagePanel?.prefWidth = win.width.toDouble()
            imagePanel?.prefHeight = win.height.toDouble()


            lCountDownLatch.countDown()

            stage.onCloseRequest = EventHandler { window.shouldClose = true }
        }

        lCountDownLatch.await()

        return window
    }

    override fun create(oldSwapchain: Swapchain?): Swapchain {
        if (glfwOffscreenWindow != -1L) {
            MemoryUtil.memFree(imageBuffer)
            sharingBuffer.close()

            // we have to get rid of the old swapchain, as we have already constructed a new surface
            if (oldSwapchain is FXSwapchain && oldSwapchain.handle != VK10.VK_NULL_HANDLE) {
                KHRSwapchain.vkDestroySwapchainKHR(device, oldSwapchain.handle, null)
            }

            KHRSurface.vkDestroySurfaceKHR(instance, surface, null)
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

        surface = VU.run(MemoryUtil.memAllocLong(1), "glfwCreateWindowSurface") {
            GLFWVulkan.glfwCreateWindowSurface(vulkanInstance, glfwOffscreenWindow, null, this)
        }

        super.create(null)

        val imageByteSize = window.width * window.height * 4L
        imageBuffer = MemoryUtil.memAlloc(imageByteSize.toInt())
        sharingBuffer = VU.createBuffer(device,
            memoryProperties, VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            wantAligned = true,
            allocationSize = imageByteSize)

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
            if (lock.tryLock() && !vulkanSwapchainRecreator.mustRecreate) {
                val imageByteSize = window.width * window.height * 4
                val buffer = sharingBuffer.mapIfUnmapped().getByteBuffer(imageByteSize)

                buffer?.let { imagePanel?.update(buffer) }

                lock.unlock()
            }
        }

        resizeHandler.queryResize()
    }

    override fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator) {
        PlatformImpl.runLater {
            stage.isFullScreen = !stage.isFullScreen
            window.isFullscreen = !window.isFullscreen

            resizeHandler.lastWidth = window.width
            resizeHandler.lastHeight = window.height
        }
    }

    override fun embedIn(panel: SceneryPanel?) {
        imagePanel = panel
    }

    override fun close() {
        KHRSwapchain.vkDestroySwapchainKHR(device, handle, null)
        KHRSurface.vkDestroySurfaceKHR(instance, surface, null)

        presentInfo.free()

        MemoryUtil.memFree(swapchainImage)
        MemoryUtil.memFree(swapchainPointer)
        MemoryUtil.memFree(imageBuffer)

        sharingBuffer.close()

        glfwDestroyWindow(glfwOffscreenWindow)
    }
}
