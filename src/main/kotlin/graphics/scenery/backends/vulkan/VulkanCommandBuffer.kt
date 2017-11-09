package graphics.scenery.backends.vulkan

import graphics.scenery.utils.LazyLogger
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.system.MemoryUtil.*
import java.nio.LongBuffer

/**
 * Vulkan Command Buffer class. Wraps command buffer and fencing functionality.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VulkanCommandBuffer(val device: VulkanDevice, var commandBuffer: VkCommandBuffer?, val fenced: Boolean = true): AutoCloseable {
    private val logger by LazyLogger()
    var stale: Boolean = true

    private var fenceInitialized: Boolean = false
    private var fence: LongBuffer = memAllocLong(1)
    var submitted = false

    init {
        if(fenced) {
            addFence()
        }
    }

    fun addFence() {
        val fc = VkFenceCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            .pNext(NULL)

        val f = VU.run(memAllocLong(1), "Creating fence",
            { vkCreateFence(device.vulkanDevice, fc, null, this) },
            { fc.free() })

        fence.put(0, f)
        fenceInitialized = true
    }

    fun waitForFence(timeout: Long? = null) {
        if(fenced && fenceInitialized) {
            VU.run(memAllocLong(1), "Waiting for fence",
                { vkWaitForFences(device.vulkanDevice, fence, true, timeout ?: -1L) })
        }
    }

    fun resetFence() {
        if(fenced && fenceInitialized) {
            VU.run(memAllocLong(1), "Resetting fence",
                { vkResetFences(device.vulkanDevice, fence) })
        }
    }

    fun getFence(): Long {
        return if(fenced) {
            fence.get(0)
        } else {
            return NULL
        }
    }

    override fun close() {
        if(fenced && fenceInitialized) {
            vkDestroyFence(device.vulkanDevice, fence.get(0), null)
        }

        memFree(fence)
    }
}
