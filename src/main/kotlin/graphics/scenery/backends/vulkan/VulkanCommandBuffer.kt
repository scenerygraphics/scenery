package graphics.scenery.backends.vulkan

import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.VkCommandBuffer
import vkn.*

/**
 * Vulkan Command Buffer class. Wraps command buffer and fencing functionality.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VulkanCommandBuffer(val device: VulkanDevice, var commandBuffer: VkCommandBuffer?, val fenced: Boolean = true) : AutoCloseable {
    private val logger by LazyLogger()
    var stale: Boolean = true

    private var fenceInitialized: Boolean = false
    private var fence: VkFence = NULL
    var submitted = false

    init {
        if (fenced)
            addFence()
    }

    fun addFence() {
        fence = device.vulkanDevice createFence vk.FenceCreateInfo { }
        fenceInitialized = true
    }

    fun waitForFence(timeout: Long? = null) {
        if (fenced && fenceInitialized)
            device.vulkanDevice.waitForFence(fence, true, timeout ?: -1L)
    }

    fun resetFence() {
        if (fenced && fenceInitialized)
            device.vulkanDevice resetFence fence
    }

    // TODO consider getting rid of fenced and check NULLability on fence
    fun getFence(): VkFence = if (fenced) fence else NULL

    override fun close() {
        if (fenced && fenceInitialized)
            device.vulkanDevice destroyFence fence
    }
}
