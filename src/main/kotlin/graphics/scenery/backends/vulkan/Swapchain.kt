package graphics.scenery.backends.vulkan

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

    fun create(oldSwapchain: Swapchain?): Swapchain
    fun present(waitForSemaphores: LongBuffer? = null)
    fun next(timeout: Long = -1L, waitForSemaphore: Long = 0L): Boolean
    override fun close()
}
