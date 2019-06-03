package graphics.scenery.backends.vulkan

import com.sun.javafx.application.PlatformImpl
import graphics.scenery.Hub
import graphics.scenery.backends.JavaFXStage
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.SceneryFXPanel
import graphics.scenery.utils.SceneryJPanel
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
import javafx.stage.Stage
import javafx.stage.Window
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VkQueue
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.CountDownLatch
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
        var p: SceneryFXPanel? = null

        Platform.runLater {
            if (imagePanel == null) {
                val s = Stage()
                p = SceneryFXPanel(win.width, win.height)
                window = JavaFXStage(p as SceneryFXPanel)

                s.title = "FXSwapchain"

                val lStackPane = StackPane()
                lStackPane.backgroundProperty()
                    .set(Background(BackgroundFill(Color.TRANSPARENT,
                        CornerRadii.EMPTY,
                        Insets.EMPTY)))

                val pane = GridPane()
                val label = Label("Experimental JavaFX Swapchain - use with caution!")

                GridPane.setHgrow(p, Priority.ALWAYS)
                GridPane.setVgrow(p, Priority.ALWAYS)

                GridPane.setFillHeight(p, true)
                GridPane.setFillWidth(p, true)

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

                imagePanel = p
                stage = s
            } else {
                imagePanel?.let {
                    window = when(it) {
                        is SceneryFXPanel -> {

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

                            stage.onCloseRequest = EventHandler { window.shouldClose = true }

                            JavaFXStage(it)
                        }
                        is SceneryJPanel -> {
                            it.addComponentListener(object: ComponentAdapter() {
                                override fun componentResized(e: ComponentEvent) {
                                    super.componentResized(e)
                                    logger.debug("SceneryJPanel component resized to ${e.component.width} ${e.component.height}")
                                    resizeHandler.lastWidth = e.component.width
                                    resizeHandler.lastHeight = e.component.height
                                }
                            })

                            SceneryWindow.SwingWindow(it)
                        }
                        else -> {
                            throw IllegalArgumentException("$it can't be ${it.javaClass.simpleName}")
                        }
                    }

                    window.width = it.panelWidth
                    window.height = it.panelHeight

                }
            }

            resizeHandler.lastWidth = window.width
            resizeHandler.lastHeight = window.height

            lCountDownLatch.countDown()
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
     * Post-present routine, copies the rendered image into the imageView of the [SceneryFXPanel].
     */
    override fun postPresent(image: Int) {
        super.postPresent(image)

        Platform.runLater {
            if (lock.tryLock() && !vulkanSwapchainRecreator.mustRecreate && sharingBuffer.initialized()) {
                val imageByteSize = window.width * window.height * 4
                val buffer = sharingBuffer.mapIfUnmapped().getByteBuffer(imageByteSize)

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
        imagePanel?.imageScaleY = 1.0f
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
        MemoryUtil.memFree(swapchainImage)
        MemoryUtil.memFree(swapchainPointer)
        MemoryUtil.memFree(imageBuffer)

        sharingBuffer.close()
    }

    companion object: SwapchainParameters {
        override var headless = true
        override var usageCondition = { p: SceneryPanel? -> System.getProperty("scenery.Renderer.UseJavaFX", "false")?.toBoolean() ?: false || p is SceneryFXPanel }
    }
}
