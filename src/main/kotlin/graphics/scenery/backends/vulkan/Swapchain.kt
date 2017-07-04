package graphics.scenery.backends.vulkan

import graphics.scenery.Hub
import graphics.scenery.backends.SceneryWindow
import org.lwjgl.vulkan.VkInstance
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

    fun createWindow(window: SceneryWindow, instance: VkInstance, swapchainRecreator: VulkanRenderer.SwapchainRecreator)
    fun create(oldSwapchain: Swapchain?): Swapchain
    fun present(waitForSemaphores: LongBuffer? = null)
    fun postPresent(image: Int)
    fun next(timeout: Long = -1L, waitForSemaphore: Long = 0L): Boolean
    override fun close()
    fun toggleFullscreen(hub: Hub, swapchainRecreator: VulkanRenderer.SwapchainRecreator)
}
