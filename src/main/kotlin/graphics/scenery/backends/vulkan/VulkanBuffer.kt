package graphics.scenery.backends.vulkan

import glfw_.appBuffer
import graphics.scenery.utils.LazyLogger
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import vkn.*
import java.nio.ByteBuffer

class VulkanBuffer(val device: VulkanDevice, val size: VkDeviceSize, val usage: VkBufferUsageFlags, val requestedMemoryProperties: VkMemoryPropertyFlags, wantAligned: Boolean = true) : AutoCloseable {
    protected val logger by LazyLogger()
    private var currentPosition = 0L
    private var currentPointer: PointerBuffer? = null
    var alignment: VkDeviceSize = 256
        private set
    var memory: VkDeviceMemory = -1L
        private set
    var vulkanBuffer: VkBuffer = -1L
        private set
    var data: Long = -1L
        private set
    var allocatedSize: VkDeviceSize = 0
        private set

    private var mapped = false

    var stagingBuffer: ByteBuffer = memAlloc(size.toInt())

    init {

        val bufferInfo = vk.BufferCreateInfo {
            usage = this@VulkanBuffer.usage
            size = this@VulkanBuffer.size
        }
        vulkanBuffer = device.vulkanDevice createBuffer bufferInfo
        val reqs = device.vulkanDevice.getBufferMemoryRequirements(vulkanBuffer)

        val size = when {
            wantAligned -> when {
                reqs.size % reqs.alignment == 0L -> reqs.size
                else -> reqs.size + reqs.alignment - (reqs.size % reqs.alignment)
            }
            else -> reqs.size
        }

        val allocInfo = vk.MemoryAllocateInfo {
            allocationSize = size
            memoryTypeIndex(device.getMemoryType(reqs.memoryTypeBits, requestedMemoryProperties)[0]) // TODO BUG
        }

        memory = device.vulkanDevice allocateMemory allocInfo
        device.vulkanDevice.bindBufferMemory(vulkanBuffer, memory)

        allocatedSize = size
        alignment = reqs.alignment
    }

    fun getPointerBuffer(size: Int): ByteBuffer {
        if (currentPointer == null) map()

        val buffer = memByteBuffer(currentPointer!![0] + currentPosition, size)
        currentPosition += size * 1L

        return buffer
    }

    fun getCurrentOffset(): Int {
        if (currentPosition % alignment != 0L) {
            currentPosition += alignment - (currentPosition % alignment)
            stagingBuffer.position(currentPosition.toInt())
        }
        return currentPosition.toInt()
    }

    fun advance(align: VkDeviceSize = alignment): Int {
        val pos = stagingBuffer.position()
        val rem = pos % align

        if (rem != 0L) {
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
        val dest = appBuffer.pointer
        device.vulkanDevice.mapMemory(memory, 0, size, 0, dest)
        memCopy(memAddress(data), memGetAddress(dest), data.remaining().toLong())
        device.vulkanDevice unmapMemory memory
    }

    fun copyTo(dest: ByteBuffer) {
        val src = appBuffer.pointer
        device.vulkanDevice.mapMemory(memory, 0, size, 0, src)
        memCopy(memGetAddress(src), memAddress(dest), dest.remaining().toLong())
        device.vulkanDevice unmapMemory memory
    }

    // TODO
    fun map(): PointerBuffer {
        val dest = memAllocPointer(1)
        vkMapMemory(device.vulkanDevice, memory, 0, size, 0, dest)

        currentPointer = dest
        mapped = true
        return dest
    }

    fun mapIfUnmapped(): PointerBuffer {
        currentPointer?.let {
            if (mapped) {
                return it.rewind()
            }
        }

        return map()
    }

    fun unmap() {
        mapped = false
        device.vulkanDevice unmapMemory memory
    }

    fun copyFromStagingBuffer() {
        stagingBuffer.flip()
        copyFrom(stagingBuffer)
    }

    fun initialized(): Boolean {
        return vulkanBuffer != -1L && memory != -1L
    }

    override fun close() {
        logger.trace("Closing buffer $this ...")

        if (mapped)
            unmap()

        memFree(stagingBuffer)

        if (memory != -1L) {
            device.vulkanDevice freeMemory memory
            memory = -1L
        }

        if (vulkanBuffer != -1L) {
            device.vulkanDevice destroyBuffer vulkanBuffer
            vulkanBuffer = -1L
        }
    }
}
