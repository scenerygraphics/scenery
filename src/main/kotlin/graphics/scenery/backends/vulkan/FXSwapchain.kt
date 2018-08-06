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

    protected lateinit var stage: Stage

    override fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow {
        vulkanInstance = device.instance
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

    override fun postPresent(image: Int) {
        super.postPresent(image)

        Platform.runLater {
            if (lock.tryLock() && !vulkanSwapchainRecreator.mustRecreate && sharingBuffer.initialized()) {
                val imageByteSize = window.width * window.height * 4
                val buffer = sharingBuffer.mapIfUnmapped().getByteBuffer(imageByteSize)

                imagePanel?.update(buffer)

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
        KHRSwapchain.vkDestroySwapchainKHR(device.vulkanDevice, handle, null)
        KHRSurface.vkDestroySurfaceKHR(device.instance, surface, null)

        presentInfo.free()

        MemoryUtil.memFree(swapchainImage)
        MemoryUtil.memFree(swapchainPointer)
        MemoryUtil.memFree(imageBuffer)

        sharingBuffer.close()

    }
}
