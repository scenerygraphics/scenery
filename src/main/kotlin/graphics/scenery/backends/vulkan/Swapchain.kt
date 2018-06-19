package graphics.scenery.backends.vulkan

import graphics.scenery.Hub
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.SceneryPanel
import java.nio.LongBuffer

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface Swapchain : AutoCloseable {
    var handle: Long
    var images: LongArray?
    var imageViews: LongArray?

    var format: Int

    fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow
    fun create(oldSwapchain: Swapchain?): Swapchain
    fun present(waitForSemaphores: LongBuffer? = null)
    fun postPresent(image: Int)
    fun next(timeout: Long = -1L, signalSemaphore: Long = 0L): Boolean
    override fun close()
    fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator)
    fun embedIn(panel: SceneryPanel?)
}
