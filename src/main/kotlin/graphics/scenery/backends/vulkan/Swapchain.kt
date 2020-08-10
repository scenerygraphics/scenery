package graphics.scenery.backends.vulkan

import graphics.scenery.Hub
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.SceneryPanel
import java.nio.LongBuffer

/**
 * Swapchain interface for [VulkanRenderer].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface Swapchain : AutoCloseable {
    var handle: Long
    var images: LongArray
    var imageViews: LongArray

    var format: Int

    /**
     * Creates a window for this swapchain, and initialiases [win] to the appropriate window
     * kind (@see[SceneryWindow]. Needs to be handed a [VulkanRenderer.SwapchainRecreator].
     * Returns the initialised [SceneryWindow].
     */
    fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow

    /**
     * Creates a new swapchain and returns it, potentially recycling or deallocating [oldSwapchain].
     */
    fun create(oldSwapchain: Swapchain?): Swapchain

    /**
     * Present routine, to be called when the image should be presented to a window or a buffer.
     * Optionally will wait on the semaphores given in [waitForSemaphores].
     */
    fun present(waitForSemaphores: LongBuffer? = null)

    /**
     * Post-present routine, e.g. for copying the rendered image or showing it in another window.
     * [image] represents the current index with respect to [images].
     */
    fun postPresent(image: Int)

    /**
     * Will signal [signalSemaphore] that the next image is ready for being written to for presenting,
     * optionally waiting for a [timeout] before failing. Returns true if the swapchain needs to be
     * recreated and false if not.
     */
    fun next(timeout: Long = -1L, signalSemaphore: Long = 0L): Boolean

    /**
     * Closes this swapchain.
     */
    override fun close()

    /**
     * Toggles fullscreen mode for this swapchain. Needs to be given a [hub] for potential interactions
     * with other components of scenery, and a [swapchainRecreator] because it might need to signal
     * for swapchain recreation.
     */
    fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator)

    /**
     * Embeds this swapchain within [panel] (@see[SceneryFXPanel]).
     */
    fun embedIn(panel: SceneryPanel?)

    /**
     * Returns the number of presented frames for this swapchain instance.
     */
    fun presentedFrames(): Long

    companion object: SwapchainParameters {
        override var headless: Boolean = false
        override var usageCondition = { _: SceneryPanel? -> true }
    }
}
