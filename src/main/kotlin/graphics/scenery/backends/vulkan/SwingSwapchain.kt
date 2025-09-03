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

            p.component = canvas
            p.layout = BorderLayout()
            p.add(canvas, BorderLayout.CENTER)

            val frame = SwingUtilities.getAncestorOfClass(JFrame::class.java, p) as JFrame

            surface = AWTVK.create(canvas, device.instance)

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
            p.addComponentListener(object : ComponentListener {
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
            })

            frame.isVisible = true
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
