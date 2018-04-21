package graphics.scenery.backends.vulkan

import glfw_.appBuffer
import graphics.scenery.Hub
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.SceneryPanel
import vkn.VkFormat
import vkn.VkSemaphore
import vkn.VkSemaphoreBuffer
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

    var format: VkFormat

    fun createWindow(win: SceneryWindow, swapchainRecreator: VulkanRenderer.SwapchainRecreator): SceneryWindow
    fun create(oldSwapchain: Swapchain?): Swapchain
    fun present(waitForSemaphore: VkSemaphore) = present(appBuffer longBufferOf waitForSemaphore)
    fun present(waitForSemaphores: VkSemaphoreBuffer? = null)
    fun postPresent(image: Int)
    fun next(timeout: Long = -1L, waitForSemaphore: Long = 0L): Boolean
    override fun close()
    fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator)
    fun embedIn(panel: SceneryPanel?)
}
