package graphics.scenery.backends.vulkan

import vkk.entities.VkDeviceSize
import java.nio.ByteBuffer

/**
 * Represents a suballocation of a VulkanBuffer [buffer], with [size] and [offset].
 */
class VulkanSuballocation(var offset: VkDeviceSize, var size: VkDeviceSize, var buffer: VulkanBuffer) {
    /** Mark this suballocation for garbage collection, can only be set true once. */
    var free: Boolean = false
        set(value) {
            field = if(free) {
                true
            } else {
                value
            }
        }

    /**
     * Returns a view of the backing buffer of the [buffer], with position and limit
     * set to the right values.
     */
    @Suppress("unused")
    fun getBuffer(): ByteBuffer {
        return buffer.stagingBuffer.duplicate()
//        b.pos = offset.i
//        b.lim = (offset + size).i
//
//        return b
    }

    /** Returns a string representation of this suballocation. */
    override fun toString(): String = "VulkanSuballocation(@$offset, size=$size)"
}
