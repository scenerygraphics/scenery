package graphics.scenery.backends.vulkan

import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.system.MemoryUtil.*
import java.nio.LongBuffer

/**
 * Created by ulrik on 10/27/2016.
 */

class VulkanCommandBuffer(val device: VkDevice, var commandBuffer: VkCommandBuffer?, wantFence: Boolean = true) {
    var fence: LongBuffer = memAllocLong(1)
    var submitted = false

    init {
        if(wantFence) {
            addFence()
        }
    }

    fun addFence() {
        val fc = VkFenceCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            .pNext(NULL)

        val f = VU.run(memAllocLong(1), "Creating fence",
            { vkCreateFence(device, fc, null, this) },
            { fc.free() })

        fence.put(0, f)
    }

    fun waitForFence(timeout: Long? = null) {

        VU.run(memAllocLong(1), "Waiting for fence",
            { vkWaitForFences(device, fence, true, timeout ?: -1L) })
    }

    fun resetFence() {
        VU.run(memAllocLong(1), "Resetting fence",
            { vkResetFences(device, fence) })
    }
}
