package scenery.backends.vulkan

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.vkMapMemory
import org.lwjgl.vulkan.VK10.vkUnmapMemory
import org.lwjgl.vulkan.VkDevice
import java.nio.ByteBuffer

class VulkanBuffer(val device: VkDevice, var memory: Long = -1L, var buffer: Long = -1L, var data: Long = -1L) {
    private var currentPosition = 0L
    private var currentPointer: PointerBuffer? = null
    var maxSize: Long = 512 * 2048L
    var alignment: Long = 256

    fun getPointerBuffer(size: Int): ByteBuffer {
        if (currentPointer == null) {
            this.map()
        }

        val buffer = memByteBuffer(currentPointer!!.get(0) + currentPosition, size)
        currentPosition += size * 1L

        return buffer
    }

    fun getCurrentOffset(): Int {
        if (currentPosition % alignment != 0L) {
            val oldPos = currentPosition
            currentPosition += alignment - (currentPosition % alignment)
        }
        return currentPosition.toInt()
    }

    fun reset() {
        currentPosition = 0L
    }

    fun copy(data: ByteBuffer) {
        val dest = memAllocPointer(1)
        vkMapMemory(device, memory, 0, maxSize * 1L, 0, dest)
        memCopy(memAddress(data), dest.get(0), data.remaining())
        vkUnmapMemory(device, memory)
        memFree(dest)
    }

    fun map(): PointerBuffer {
        val dest = memAllocPointer(1)
        vkMapMemory(device, memory, 0, maxSize * 1L, 0, dest)

        currentPointer = dest
        return dest
    }
}
