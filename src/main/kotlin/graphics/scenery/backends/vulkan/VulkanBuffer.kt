package graphics.scenery.backends.vulkan

import graphics.scenery.utils.LazyLogger
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer

class VulkanBuffer(val device: VulkanDevice, val size: Long, val usage: Int, val requestedMemoryProperties: Int, wantAligned: Boolean = true): AutoCloseable {
    protected val logger by LazyLogger()
    private var currentPosition = 0L
    private var currentPointer: PointerBuffer? = null
    var alignment: Long = 256
        private set
    var memory: Long = -1L
        private set
    var vulkanBuffer: Long = -1L
        private set
    var data: Long = -1L
        private set
    var allocatedSize: Long = 0
        private set

    private var mapped = false

    var stagingBuffer: ByteBuffer = memAlloc(size.toInt())

    init {
        val memory = MemoryUtil.memAllocLong(1)
        val memTypeIndex = MemoryUtil.memAllocInt(1)

        val reqs = VkMemoryRequirements.calloc()
        val bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .pNext(NULL)
            .usage(usage)
            .size(size)

        val buffer = VU.getLong("Creating buffer",
            { vkCreateBuffer(device.vulkanDevice, bufferInfo, null, this) }, {})
        vkGetBufferMemoryRequirements(device.vulkanDevice, buffer, reqs)

        val size = if (wantAligned) {
            if (reqs.size().rem(reqs.alignment()) == 0L) {
                reqs.size()
            } else {
                reqs.size() + reqs.alignment() - (reqs.size().rem(reqs.alignment()))
            }
        } else {
            reqs.size()
        }

        val allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)
            .allocationSize(size)
            .memoryTypeIndex(device.getMemoryType(reqs.memoryTypeBits(), requestedMemoryProperties).second)


        vkAllocateMemory(device.vulkanDevice, allocInfo, null, memory)
        vkBindBufferMemory(device.vulkanDevice, buffer, memory.get(0), 0)

        this.memory = memory.get(0)
        this.vulkanBuffer = buffer
        this.allocatedSize = size
        this.alignment = reqs.alignment()

        bufferInfo.free()
        reqs.free()
        allocInfo.free()
        MemoryUtil.memFree(memTypeIndex)
        MemoryUtil.memFree(memory)
    }

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
        vkMapMemory(device.vulkanDevice, memory, 0, size, 0, dest)
        memCopy(memAddress(data), dest.get(0), data.remaining().toLong())
        vkUnmapMemory(device.vulkanDevice, memory)
        memFree(dest)
    }

    fun copyTo(dest: ByteBuffer) {
        val src = memAllocPointer(1)
        vkMapMemory(device.vulkanDevice, memory, 0, size, 0, src)
        memCopy(src.get(0), memAddress(dest), dest.remaining().toLong())
        vkUnmapMemory(device.vulkanDevice, memory)
        memFree(src)
    }

    fun map(): PointerBuffer {
        val dest = memAllocPointer(1)
        vkMapMemory(device.vulkanDevice, memory, 0, size, 0, dest)

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
        vkUnmapMemory(device.vulkanDevice, memory)
    }

    fun copyFromStagingBuffer() {
        stagingBuffer.flip()
        copyFrom(stagingBuffer)
    }

    fun initialized(): Boolean {
        return ((vulkanBuffer != -1L) && (memory != -1L))
    }

    override fun close() {
        logger.trace("Closing buffer $this ...")

        if(mapped) {
            unmap()
        }

        memFree(stagingBuffer)

        if(memory != -1L) {
            vkFreeMemory(device.vulkanDevice, memory, null)
            memory = -1L
        }

        if(vulkanBuffer != -1L) {
            vkDestroyBuffer(device.vulkanDevice, vulkanBuffer, null)
            vulkanBuffer = -1L
        }
    }
}
