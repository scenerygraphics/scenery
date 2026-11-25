package graphics.scenery.backends.vulkan

import graphics.scenery.Hub
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.SceneryJPanel
import graphics.scenery.utils.SceneryPanel
import graphics.scenery.utils.lazyLogger
import org.lwjgl.system.MemoryUtil.memFree
import org.lwjgl.system.Platform
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR
import org.lwjgl.vulkan.KHRSwapchain
import org.lwjgl.vulkan.VK10.vkDestroyFence
import org.lwjgl.vulkan.VK10.vkQueueWaitIdle
import org.lwjgl.vulkan.awt.AWTVK
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * GLFW-based default Vulkan Swapchain and window, residing on [device], associated with [queue].
 * Needs to be given [commandPools] to allocate command buffers from. [useSRGB] determines whether
 * the sRGB colorspace will be used, [vsync] determines whether vertical sync will be forced (swapping
 * rendered images in sync with the screen's frequency). [undecorated] determines whether the created
 * window will have the window system's default chrome or not.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class SwingSwapchain(override val device: VulkanDevice,
                          override val queue: VulkanDevice.QueueWithMutex,
                          override val commandPools: VulkanRenderer.CommandPools,
                          override val renderConfig: RenderConfigReader.RenderConfig,
                          override val useSRGB: Boolean = true,
                          override val vsync: Boolean = true,
                          override val undecorated: Boolean = false) : VulkanSwapchain(device, queue, commandPools, renderConfig, useSRGB, vsync, undecorated) {

    private val WINDOW_RESIZE_TIMEOUT: Long = 500_000_000

    protected var sceneryPanel: SceneryPanel? = null
    var mainFrame: JFrame? = null
        protected set

    /**
     * Creates a window for this swapchain, and initialiases [win] as [SceneryWindow.GLFWWindow].
     * Needs to be handed a [VulkanRenderer.SwapchainRecreator].
     * Returns the initialised [SceneryWindow].
     */
    override fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow {
        val windowCreator = Runnable {
            val p = sceneryPanel as? SceneryJPanel ?: throw IllegalArgumentException("Must have SwingWindow")

            val canvas = Canvas()
            // The canvas is only ever painted by native Vulkan/Metal presentation, never by AWT's
            // own paint machinery. Without this, AWT can still issue a repaint of the canvas (e.g.
            // during/after the deferred layout pass) that fills it with its plain background color;
            // since nothing then tells AWT to repaint it again, that fill can end up staying on top
            // of the Metal layer's content permanently, even though presentation keeps succeeding
            // underneath it.
            canvas.ignoreRepaint = true

            p.component = canvas
            p.layout = BorderLayout()
            p.add(canvas, BorderLayout.CENTER)
            // Force the BorderLayout pass synchronously here, instead of leaving it to a deferred
            // EDT task -- the wait loop below runs on the EDT too and would otherwise be able to
            // starve that deferred layout, leaving [canvas] at a stale/zero size when the surface
            // is created from it.
            p.validate()

            val frame = SwingUtilities.getAncestorOfClass(JFrame::class.java, p) as JFrame

            // Per LWJGL's own AWTVK docs: "Before the canvas can be passed into
            // AWTVK#create(Canvas, VkInstance), it must have a peer, which can be forced by calling
            // Frame#pack()." The frame was already packed once in createApplicationFrame(), but that
            // was before [canvas] existed -- pack() must be called again now that it's been added,
            // to force real native peer creation for the canvas specifically, not just a layout pass.
            frame.pack()

            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    super.windowClosing(e)

                    vkDestroySurfaceKHR(device.instance, surface, null)
                }

            })

            window = SceneryWindow.SwingWindow(p)
            window.width = win.width
            window.height = win.height

            // the listener should only be initialized here, otherwise [window]
            // might be uninitialized.
            // Attached to both [p] and [canvas]: [p]'s size only changes on an actual window
            // resize, but [canvas]'s bounds (set by [p]'s BorderLayout) can also change on their
            // own, e.g. once a deferred layout pass finally runs -- that must trigger a swapchain
            // recreate too, or a canvas that grew/shrank after surface creation is never corrected.
            val resizeListener = object : ComponentListener {
                override fun componentResized(e: ComponentEvent) {
                    if (lastResize > 0L && lastResize + WINDOW_RESIZE_TIMEOUT > System.nanoTime()) {
                        return
                    }

                    if (e.component.width <= 0 || e.component.height <= 0) {
                        return
                    }

                    window.width = e.component.width
                    window.height = e.component.height

                    logger.debug("Resizing panel to ${window.width}x${window.height}")
                    swapchainRecreator.mustRecreate = true
                    lastResize = System.nanoTime()
                }

                override fun componentMoved(e: ComponentEvent) {}
                override fun componentHidden(e: ComponentEvent) {}
                override fun componentShown(e: ComponentEvent) {}
            }
            p.addComponentListener(resizeListener)
            canvas.addComponentListener(resizeListener)

            frame.isVisible = true

            // On macOS in particular, the AWT/Cocoa native peer behind [canvas] (and the CAMetalLayer
            // backing it) is not guaranteed to be fully realised the instant frame.isVisible is set --
            // peer/layer attachment happens asynchronously on the AppKit main thread. Creating the
            // Vulkan surface before that finishes binds it to a layer the window server never
            // composites, or binds it while canvas still has a stale/zero size: the window opens,
            // but nothing ever renders. Wait here for the canvas to actually be showing, bound to a
            // valid GraphicsConfiguration, and to have a real (non-zero) size before creating the surface.
            fun canvasReady() = canvas.isShowing && canvas.graphicsConfiguration != null && canvas.width > 0 && canvas.height > 0
            val readyDeadline = System.nanoTime() + 2_000_000_000L
            while (!canvasReady() && System.nanoTime() < readyDeadline) {
                Thread.sleep(5)
            }
            if (!canvasReady()) {
                logger.warn("Canvas did not become showing/ready within timeout, proceeding anyway")
            }

            surface = AWTVK.create(canvas, device.instance)
        }

        if(SwingUtilities.isEventDispatchThread()) {
            windowCreator.run()
        } else {
            SwingUtilities.invokeAndWait(windowCreator)
        }

        return window
    }

    /**
     * Changes the current window to fullscreen.
     */
    override fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator) {
        // TODO: Add
    }

    /**
     * Embeds the swapchain into a [SceneryPanel].
     */
    override fun embedIn(panel: SceneryPanel?) {
        if(panel == null) {
            return
        }

        sceneryPanel = panel
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
        vkQueueWaitIdle(presentQueue.queue)
        vkQueueWaitIdle(queue.queue)

        logger.debug("Closing swapchain {}", this)

        closeSyncPrimitives()

        KHRSwapchain.vkDestroySwapchainKHR(device.vulkanDevice, handle, null)
        vkDestroySurfaceKHR(device.instance, surface, null)

        (sceneryPanel as? SceneryJPanel)?.remove(0)
        presentInfo.free()
        memFree(swapchainImage)
        memFree(swapchainPointer)
    }

    companion object: SwapchainParameters {
        private val logger by lazyLogger()
        override var headless = false
        override var usageCondition = { p: SceneryPanel? ->
            when {
                System.getProperty(Renderer.HEADLESS_PROPERTY_NAME, "false").toBoolean() -> false
                System.getProperty("scenery.Renderer.UseAWT", "false").toBoolean() -> true
                p is SceneryJPanel -> true
                Platform.get() == Platform.MACOSX
                        && !System.getProperty(Renderer.HEADLESS_PROPERTY_NAME, "false").toBoolean() -> true
                else -> false
            }
        }

        /**
         * Creates a new JFrame-based application frame. The title of the window will be set to [applicationName],
         * the width and height will be set according to [windowWidth] and [windowHeight]. The function will
         * return a new [SceneryJPanel].
         */
        fun createApplicationFrame(applicationName: String, windowWidth: Int, windowHeight: Int): SceneryJPanel {
            var p: SceneryJPanel? = null
            val creator = Runnable {
                logger.debug("Creating JFrame in SwingSwapchain, ${windowWidth}x${windowHeight}")
                val mainFrame = JFrame(applicationName)
                mainFrame.layout = BorderLayout()

                val sceneryPanel = SceneryJPanel(owned = true)
                sceneryPanel.preferredSize = Dimension(windowWidth, windowHeight)
                mainFrame.add(sceneryPanel, BorderLayout.CENTER)
                mainFrame.pack()

                p = sceneryPanel
            }

            if(SwingUtilities.isEventDispatchThread()) {
                creator.run()
            } else {
                SwingUtilities.invokeAndWait(creator)
            }

            val panel = p
            if(panel == null) {
                throw IllegalStateException("SceneryJPanel did not initialise correctly.")
            } else {
                return panel
            }
        }

    }
}
