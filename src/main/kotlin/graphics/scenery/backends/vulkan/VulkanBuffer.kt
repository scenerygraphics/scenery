package graphics.scenery.backends.vulkan

import graphics.scenery.utils.LazyLogger
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Vulkan buffer class, creates a buffer residing on [device], with a [size] and a defined [usage].
 * The buffer may have [requestedMemoryProperties].
 *
 * @param[wantAligned] - whether the buffer should be aligned
 */
class VulkanBuffer(val device: VulkanDevice, val size: Long, val usage: Int, val requestedMemoryProperties: Int, val wantAligned: Boolean = true): AutoCloseable {
    private val logger by LazyLogger()
    private var currentPosition = 0L
    private var currentPointer: PointerBuffer? = null

    /** Buffer alignment, 256 bytes by default (Vulkan standard) */
    var alignment: Long = 256
        private set
    /** Memory pointer */
    var memory: Long = -1L
        private set
    /** Buffer to the Vulkan pointer struct */
    var vulkanBuffer: Long = -1L
        private set
    /** Final allocated size of the buffer, might be different from the requested size, due to alignment. */
    var allocatedSize: Long = 0
        private set

    private var mapped = false

    /** Staging buffer, providing host memory */
    var stagingBuffer: ByteBuffer = memAlloc(size.toInt())
        private set

    init {
        val b = allocateVulkanBuffer(size, wantAligned)

        this.memory = b.memory
        this.vulkanBuffer = b.buffer
        this.allocatedSize = b.size
        this.alignment = b.alignment
    }

    private data class RawBuffer(val buffer: Long, val memory: Long, val size: Long, val alignment: Long)

    private fun allocateVulkanBuffer(size: Long, wantAligned: Boolean): RawBuffer {
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

        val actualSize = if (wantAligned) {
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
            .allocationSize(actualSize)
            .memoryTypeIndex(device.getMemoryType(reqs.memoryTypeBits(), requestedMemoryProperties).first())


        VU.run("Allocating memory", { vkAllocateMemory(device.vulkanDevice, allocInfo, null, memory) })
        VU.run("Binding buffer memory", { vkBindBufferMemory(device.vulkanDevice, buffer, memory.get(0), 0) })

        val r = RawBuffer(buffer, memory.get(0), actualSize, reqs.alignment())

        bufferInfo.free()
        reqs.free()
        allocInfo.free()
        MemoryUtil.memFree(memTypeIndex)
        MemoryUtil.memFree(memory)

        return r
    }

    /**
     * Resizes the backing buffer to [newSize], which is 1.5x the original size by default,
     * and returns the staging buffer.
     */
    fun resize(newSize: Int = (size * 1.5f).roundToInt()): ByteBuffer {
        destroyVulkanBuffer()
        stagingBuffer = MemoryUtil.memRealloc(stagingBuffer, newSize) ?: throw IllegalStateException("Could not resize buffer")

        val b = allocateVulkanBuffer(newSize * 1L, wantAligned)

        this.memory = b.memory
        this.vulkanBuffer = b.buffer
        this.allocatedSize = b.size
        this.alignment = b.alignment

        return stagingBuffer
    }

    /**
     * Advances this buffer to the next possible aligned position,
     * override the buffer's default alignment by setting [align] to
     * the desired value. Returns the new position.
     */
    fun advance(align: Long = this.alignment): Int {
        val pos = stagingBuffer.position()
        val rem = pos.rem(align)

        if(rem != 0L) {
            val newpos = pos + align.toInt() - rem.toInt()
            stagingBuffer.position(newpos)
        }

        return stagingBuffer.position()
    }

    /**
     * Resets the staging buffer such that it can be again filled
     * from the beginning.
     */
    fun reset() {
        stagingBuffer.position(0)
        stagingBuffer.limit(size.toInt())
        currentPosition = 0L
    }

    /**
     * Copies data from the [ByteBuffer] [data] directly to the device memory.
     */
    fun copyFrom(data: ByteBuffer) {
        val dest = memAllocPointer(1)
        vkMapMemory(device.vulkanDevice, memory, 0, size, 0, dest)
        memCopy(memAddress(data), dest.get(0), data.remaining().toLong())
        vkUnmapMemory(device.vulkanDevice, memory)
        memFree(dest)
    }

    /**
     * Copies the contents of the device buffer to [dest].
     */
    fun copyTo(dest: ByteBuffer) {
        val src = memAllocPointer(1)
        vkMapMemory(device.vulkanDevice, memory, 0, size, 0, src)
        memCopy(src.get(0), memAddress(dest), dest.remaining().toLong())
        vkUnmapMemory(device.vulkanDevice, memory)
        memFree(src)
    }

    /**
     * Maps this buffer
     */
    fun map(): PointerBuffer {
        val dest = memAllocPointer(1)
        vkMapMemory(device.vulkanDevice, memory, 0, size, 0, dest)

        currentPointer = dest
        mapped = true
        return dest
    }

    /**
     * Maps this buffer it wasn't mapped before, and rewinds it.
     */
    fun mapIfUnmapped(): PointerBuffer {
        currentPointer?.let {
            if(mapped) {
                return it.rewind()
            }
        }

        return map()
    }

    /**
     * Unmaps this buffer.
     */
    fun unmap() {
        mapped = false
        vkUnmapMemory(device.vulkanDevice, memory)
    }

    /**
     * Copies data from the [stagingBuffer] to device memory.
     */
    fun copyFromStagingBuffer() {
        stagingBuffer.flip()
        copyFrom(stagingBuffer)
    }

    /**
     * Returns true if this buffer is initialised correctly.
     */
    fun initialized(): Boolean {
        return ((vulkanBuffer != -1L) && (memory != -1L))
    }

    private fun destroyVulkanBuffer() {
        if(memory == -1L || vulkanBuffer == -1L) {
            return
        }

        vkFreeMemory(device.vulkanDevice, memory, null)
        memory = -1L

        vkDestroyBuffer(device.vulkanDevice, vulkanBuffer, null)
        vulkanBuffer = -1L
    }

    /**
     * Closes this buffer, freeing all allocated resources on host and device.
     */
    override fun close() {
        if(memory == -1L || vulkanBuffer == -1L) {
            return
        }

        logger.trace("Closing buffer $this ...")

        if(mapped) {
            unmap()
        }

        memFree(stagingBuffer)

        vkFreeMemory(device.vulkanDevice, memory, null)
        memory = -1L

        vkDestroyBuffer(device.vulkanDevice, vulkanBuffer, null)
        vulkanBuffer = -1L
    }
}
