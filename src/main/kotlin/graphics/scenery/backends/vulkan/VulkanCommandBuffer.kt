package graphics.scenery.backends.vulkan

import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkFenceCreateInfo
import java.nio.LongBuffer

/**
 * Vulkan Command Buffer class. Wraps command buffer and fencing functionality.
 * Allocates the command buffer on [device], and can wrap an existing raw [commandBuffer].
 * [VulkanCommandBuffer]s are by default [fenced].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VulkanCommandBuffer(val device: VulkanDevice, var commandBuffer: VkCommandBuffer?, val fenced: Boolean = true): AutoCloseable {
    /** Whether this command buffer is stale and needs to be re-recorded. */
    var stale: Boolean = true

    private var fenceInitialized: Boolean = false
    private var fence: LongBuffer = memAllocLong(1)

    /** Whether this command buffer has already been submitted to a queue. */
    var submitted = false

    init {
        if(fenced) {
            addFence()
        }
    }

    /**
     * Adds a fence to this command buffer for synchronisation.
     */
    fun addFence() {
        val fc = VkFenceCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            .pNext(NULL)

        val f = VU.getLong("Creating fence",
            { vkCreateFence(device.vulkanDevice, fc, null, this) },
            { fc.free() })

        fence.put(0, f)
        fenceInitialized = true
    }

    /**
     * Waits for the command buffer's execution to complete via a fence,
     * waiting for [timeout] milliseconds.
     */
    fun waitForFence(timeout: Long? = null) {
        if(fenced && fenceInitialized) {
            VU.getLong("Waiting for fence",
                { vkWaitForFences(device.vulkanDevice, fence, true, timeout ?: -1L) }, {})
        }
    }

    /**
     * Resets this command buffer's fence.
     */
    fun resetFence() {
        if(fenced && fenceInitialized) {
            VU.getLong("Resetting fence",
                { vkResetFences(device.vulkanDevice, fence) }, {})
        }
    }

    /**
     * Returns a reference to the fence, or null if the command buffer is not [fenced].
     */
    fun getFence(): Long {
        return if(fenced) {
            fence.get(0)
        } else {
            return NULL
        }
    }

    /**
     * Closes and deallocates this command buffer.
     */
    override fun close() {
        if(fenced && fenceInitialized) {
            vkDestroyFence(device.vulkanDevice, fence.get(0), null)
        }

        memFree(fence)
    }

    fun prepareAndStartRecording(pool: Long): VkCommandBuffer {
        // start command buffer recording
        if (commandBuffer == null) {
            commandBuffer = VU.newCommandBuffer(device, pool, autostart = true)
        }

        val cmd = commandBuffer ?: throw IllegalStateException("Command buffer cannot be null for recording")

        vkResetCommandBuffer(cmd, 0)
        VU.beginCommandBuffer(cmd)

        return cmd
    }
}
