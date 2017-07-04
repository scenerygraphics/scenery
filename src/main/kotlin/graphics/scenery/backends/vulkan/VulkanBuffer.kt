package graphics.scenery.backends.vulkan

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDevice
import java.nio.ByteBuffer

class VulkanBuffer(val device: VkDevice, var memory: Long = -1L, var buffer: Long = -1L, var data: Long = -1L, val size: Long): AutoCloseable {
    private var currentPosition = 0L
    private var currentPointer: PointerBuffer? = null
    var alignment: Long = 256
    private var mapped = false

    var stagingBuffer: ByteBuffer = memAlloc(size.toInt())

    fun getPointerBuffer(size: Int): ByteBuffer {
        if (currentPointer == null) {
            this.map()
        }

        val buffer = memByteBuffer(currentPointer!!.get(0) + currentPosition, size)
        currentPosition += size * 1L

        return buffer
    }

    fun getCurrentOffset(): Int {
        if (currentPosition.rem(alignment) != 0L) {
            currentPosition += alignment - currentPosition.rem(alignment)
            stagingBuffer.position(currentPosition.toInt())
        }
        return currentPosition.toInt()
    }

    fun advance(align: Long = this.alignment): Int {
        val pos = stagingBuffer.position()
        val rem = pos.rem(align)

        if(rem != 0L) {
            val newpos = pos + align.toInt() - rem.toInt()
            stagingBuffer.position(newpos)
        }

        return stagingBuffer.position()
    }

    fun reset() {
        stagingBuffer.position(0)
        stagingBuffer.limit(size.toInt())
        currentPosition = 0L
    }

    fun copyFrom(data: ByteBuffer) {
        val dest = memAllocPointer(1)
        vkMapMemory(device, memory, 0, size, 0, dest)
        memCopy(memAddress(data), dest.get(0), data.remaining())
        vkUnmapMemory(device, memory)
        memFree(dest)
    }

    fun copyTo(dest: ByteBuffer) {
        val src = memAllocPointer(1)
        vkMapMemory(device, memory, 0, size, 0, src)
        memCopy(src.get(0), memAddress(dest), dest.remaining())
        vkUnmapMemory(device, memory)
        memFree(src)
    }

    fun map(): PointerBuffer {
        val dest = memAllocPointer(1)
        vkMapMemory(device, memory, 0, size, 0, dest)

        currentPointer = dest
        mapped = true
        return dest
    }

    fun mapIfUnmapped(): PointerBuffer {
        currentPointer?.let {
            if(mapped) {
                return it.rewind()
            }
        }

        return map()
    }

    fun unmap() {
        mapped = false
        vkUnmapMemory(device, memory)
    }

    fun copyFromStagingBuffer() {
        stagingBuffer.flip()
        copyFrom(stagingBuffer)
    }

    override fun close() {
        if(mapped) {
            unmap()
        }

        memFree(stagingBuffer)

        if(memory != -1L) {
            vkFreeMemory(device, memory, null)
        }

        if(buffer != -1L) {
            vkDestroyBuffer(device, buffer, null)
        }
    }
}
