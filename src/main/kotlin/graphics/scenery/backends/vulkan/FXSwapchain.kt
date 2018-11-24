package graphics.scenery.backends.vulkan

import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
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
import javafx.stage.Window
import kool.free
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil.memByteBuffer
import java.util.concurrent.locks.ReentrantLock


/**
 * Extended Vulkan swapchain compatible with JavaFX, inherits from [HeadlessSwapchain], and
 * adds JavaFX-specific functionality.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class FXSwapchain(device: VulkanDevice,
                  queue: VkQueue,
                  commandPools: VulkanRenderer.CommandPools,
                  renderConfig: RenderConfigReader.RenderConfig,
                  useSRGB: Boolean = true,
                  useFramelock: Boolean = false,
                  bufferCount: Int = 2) : HeadlessSwapchain(device, queue, commandPools, renderConfig, useSRGB, useFramelock, bufferCount) {
    var lock = ReentrantLock()

    protected lateinit var stage: Window

    /**
     * Creates a window for this swapchain, and initialiases [win] as [SceneryWindow.JavaFXStage].
     * In this case, only a proxy window is used, without any actual window creation.
     */
    override fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow {
        vulkanInstance = device.instance
        vulkanSwapchainRecreator = swapchainRecreator

        PlatformImpl.startup { }
        val lCountDownLatch = CountDownLatch(1)
        Platform.runLater {
            if (imagePanel == null) {
                val s = Stage()
                s.title = "FXSwapchain"

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
                s.scene = scene
                s.show()

                window.width = win.width
                window.height = win.height

                stage = s
            } else {
                imagePanel?.let {
                    window = SceneryWindow.JavaFXStage(it)
                    window.width = it.width.toInt()
                    window.height = it.height.toInt()

                    stage = it.scene.window

                    it.widthProperty().addListener { _, _, newWidth ->
                        resizeHandler.lastWidth = newWidth.toInt()
                    }

                    it.heightProperty().addListener { _, _, newHeight ->
                        resizeHandler.lastHeight = newHeight.toInt()
                    }

                    it.minWidth = 100.0
                    it.minHeight = 100.0
                    it.prefWidth = window.width.toDouble()
                    it.prefHeight = window.height.toDouble()
                }
            }

            resizeHandler.lastWidth = window.width
            resizeHandler.lastHeight = window.height

            lCountDownLatch.countDown()

            stage.onCloseRequest = EventHandler { window.shouldClose = true }
        }

        lCountDownLatch.await()

        return window
    }

    /**
     * Creates a new swapchain, potentially recycling or deallocating [oldSwapchain].
     */
    override fun create(oldSwapchain: Swapchain?): Swapchain {
        imagePanel?.displayedFrames = 0
        return super.create(oldSwapchain)
    }

    /**
     * Post-present routine, copies the rendered image into the imageView of the [SceneryPanel].
     */
    override fun postPresent(image: Int) {
        super.postPresent(image)

        Platform.runLater {
            if (lock.tryLock() && !vulkanSwapchainRecreator.mustRecreate && sharingBuffer.initialized()) {
                val imageByteSize = window.width * window.height * 4
                val buffer = memByteBuffer(sharingBuffer.mapIfUnmapped(), imageByteSize)

                logger.trace("Updating with {}x{}", window.width, window.height)
                imagePanel?.update(buffer)

                lock.unlock()
            }
        }

        resizeHandler.queryResize()
    }

    /**
     * Toggles fullscreen.
     */
    override fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator) {
        PlatformImpl.runLater {
            val s = stage
            if(s is Stage) {
                s.isFullScreen = !s.isFullScreen
            }

            window.isFullscreen = !window.isFullscreen

            resizeHandler.lastWidth = window.width
            resizeHandler.lastHeight = window.height
        }
    }

    /**
     * Embeds the swapchain into a [SceneryPanel].
     */
    override fun embedIn(panel: SceneryPanel?) {
        imagePanel = panel
        imagePanel?.imageView?.scaleY = 1.0
    }

    /**
     * Returns the number of frames presented with the current swapchain.
     */
    override fun presentedFrames(): Long {
        return imagePanel?.displayedFrames ?: 0
    }

    /**
     * Closes the swapchain, deallocating all resources.
     */
    override fun close() {
        swapchainPointer.free()
        imageBuffer.free()

        sharingBuffer.close()
    }
}
