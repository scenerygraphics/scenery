package graphics.scenery.backends.vulkan

import graphics.scenery.utils.lazyLogger
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Vulkan buffer class, creates a buffer residing on [device], with a [size] and a defined [usage].
 * The buffer may have [requestedMemoryProperties]. The buffer can be created with a [suballocation]
 * from a [VulkanBufferPool] -- if created in this way it'll not manage its own memory, but leave that
 * to the pool.
 *
 * @param[wantAligned] - whether the buffer should be aligned
 */
open class VulkanBuffer(val device: VulkanDevice, var size: Long,
                   val usage: Int, val requestedMemoryProperties: Int = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                   val wantAligned: Boolean = true, var suballocation: VulkanSuballocation? = null, var name : String = "VulkanBuffer"): AutoCloseable {
    private val logger by lazyLogger()
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
    /** Buffer offset. */
    var bufferOffset: Long = 0
        private set

    /** Last time buffer parameters, such as size, or id have changed */
    var updated: Long = 0
        private set

    private var mapped = false
    private var bufferReallocNeeded: Boolean = false

    /** Staging buffer, providing host memory */
    var stagingBuffer: ByteBuffer = memAlloc(size.toInt())
        private set

    init {
        if(suballocation == null) {
            val b = allocateVulkanBuffer(size, wantAligned, name)

            this.memory = b.memory
            this.vulkanBuffer = b.buffer
            this.allocatedSize = b.size
            this.alignment = b.alignment
        } else {
            suballocation?.let { sa ->
                this.memory = sa.buffer.memory
                this.vulkanBuffer = sa.buffer.vulkanBuffer
                this.allocatedSize = sa.size.toLong()
                this.alignment = sa.buffer.alignment

                this.bufferOffset = sa.offset.toLong()
                this.updated = System.nanoTime()

                logger.trace("Created suballocated Vulkan Buffer {} with memory {} of size={} bytes",  this.vulkanBuffer.toHexString(), this.memory.toHexString(), this.allocatedSize)
            }
        }
    }

    private data class RawBuffer(val buffer: Long, val memory: Long, val size: Long, val alignment: Long)

    private fun allocateVulkanBuffer(size: Long, wantAligned: Boolean, debugName : String): RawBuffer {
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

        device.tag(buffer, VulkanDevice.VulkanObjectType.Buffer, debugName)

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


        VU.run("Allocating memory (size=$actualSize)", { vkAllocateMemory(device.vulkanDevice, allocInfo, null, memory) })
        VU.run("Binding buffer memory", { vkBindBufferMemory(device.vulkanDevice, buffer, memory.get(0), 0) })

        val r = RawBuffer(buffer, memory.get(0), actualSize, reqs.alignment())

        bufferInfo.free()
        reqs.free()
        allocInfo.free()
        MemoryUtil.memFree(memTypeIndex)
        MemoryUtil.memFree(memory)

        val count = totalBuffers.incrementAndGet()
        logger.trace("Created {}{}Vulkan Buffer {} with memory {} of size={} bytes, total buffers: {}", if(suballocation != null) { "suballocated "} else {" "}, if(wantAligned) { "aligned " } else {" "}, buffer.toHexString(), r.memory.toHexString(), actualSize, count)
        this.updated = System.nanoTime()

        return r
    }

    /**
     * Resizes the backing buffer to [newSize], which is 1.5x the original size by default,
     * and returns the staging buffer.
     */
    @Synchronized fun resize(newSize: Int = (size * 1.5f).roundToInt()): ByteBuffer {
        if(mapped) {
            unmap()
        }

        logger.trace("Querying buffer resize for ${vulkanBuffer.toHexString()}, $size to $newSize bytes")
        stagingBuffer = MemoryUtil.memRealloc(stagingBuffer, newSize) ?: throw IllegalStateException("Could not resize buffer")
        size = newSize.toLong()
        bufferReallocNeeded = true

        return stagingBuffer
    }

    /**
     * Resizes the actual Vulkan buffer. Called upon copying the staging buffer to the Vulkan buffer.
     */
    @Synchronized protected fun resizeLazy() {
        if(!bufferReallocNeeded) {
            return
        }
        unmap()

        destroyVulkanBuffer()
        val b = allocateVulkanBuffer(stagingBuffer.capacity() * 1L, wantAligned, name)

        this.memory = b.memory
        this.vulkanBuffer = b.buffer
        this.allocatedSize = b.size
        this.alignment = b.alignment
        logger.trace("Buffer resized for {}, allocated size: {}", vulkanBuffer.toHexString(), allocatedSize)

        bufferReallocNeeded = false
    }

    /**
     * Advances this buffer to the next possible aligned position,
     * override the buffer's default alignment by setting [align] to
     * the desired value. Returns the new position.
     *
     * Note: 256 seems to be the safe value here, despite devices
     * reporting values of 16 or 64 as minUniformBufferOffsetAlignment.
     * So we take the maximum value of buffer alignment, or 256.
     */
    fun advance(align: Long = maxOf(this.alignment, 256)): Int {
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
    fun copyFrom(data: ByteBuffer, dstOffset: Long = 0) {
        resizeLazy()

        val dest = memAllocPointer(1)

        val dstSize = if(dstOffset > 0) {
            size - dstOffset
        } else {
            size
        }

        vkMapMemory(device.vulkanDevice, memory, bufferOffset + dstOffset, dstSize, 0, dest)
        memCopy(memAddress(data), dest.get(0), data.remaining().toLong())
        vkUnmapMemory(device.vulkanDevice, memory)
        memFree(dest)
    }

    /**
     * Copies data into the Vulkan buffer from a list of [chunks].
     */
    fun copyFrom(chunks: List<ByteBuffer>, keepMapped: Boolean = false) {
        resizeLazy()

        var currentOffset = 0

        val dest = mapIfUnmapped()
        chunks.forEach { chunk ->
            val chunkSize = chunk.remaining()
            memCopy(memAddress(chunk), dest.get(0) + currentOffset, chunk.remaining().toLong())
            currentOffset += chunkSize
        }
        if(!keepMapped) {
            vkUnmapMemory(device.vulkanDevice, memory)
        }
    }

    /**
     * Copies the contents of the device buffer to [dest].
     */
    fun copyTo(dest: ByteBuffer) {
        val src = mapIfUnmapped()
        memCopy(src.get(0), memAddress(dest), dest.remaining().toLong())
    }

    /**
     * Maps this buffer
     */
    fun map(): PointerBuffer {
        resizeLazy()

        if(!mapped) {
            val dest = memAllocPointer(1)
            vkMapMemory(device.vulkanDevice, memory, bufferOffset, size, 0, dest)
            currentPointer = dest
            mapped = true
        }

        return currentPointer!!
    }

    /**
     * Maps this buffer it wasn't mapped before, and rewinds it.
     */
    fun mapIfUnmapped(): PointerBuffer {
        resizeLazy()

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
        if(mapped) {
            mapped = false
            vkUnmapMemory(device.vulkanDevice, memory)
        }
    }

    /**
     * Copies data from the [stagingBuffer] to device memory.
     */
    fun copyFromStagingBuffer() {
        resizeLazy()

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

        if(suballocation != null) {
            logger.trace("Marking suballocation as free")
            suballocation?.free = true
            return
        }

        val count = totalBuffers.getAndDecrement()

        if(mapped) {
            unmap()
        }

        logger.trace("Closed buffer {} ({}, mem={}, total buffers: {}", this, vulkanBuffer.toHexString(), memory.toHexString(), count)

        memFree(stagingBuffer)

        vkFreeMemory(device.vulkanDevice, memory, null)
        memory = -1L

        vkDestroyBuffer(device.vulkanDevice, vulkanBuffer, null)
        vulkanBuffer = -1L
    }

    /**
     * Factory methods for [VulkanBuffer].
     */
    companion object {
        protected val totalBuffers = AtomicInteger(0)

        /**
         * Creates a new VulkanBuffer of [size] that has it's memory managed by a [VulkanBufferPool]
         * given by [pool].
         */
        fun fromPool(pool: VulkanBufferPool, size: Long, name : String): VulkanBuffer {
            val suballocation = pool.create(size.toInt())
            return VulkanBuffer(pool.device, suballocation.size.toLong(),
                usage = pool.usage, suballocation = suballocation, name = name)
        }
    }
}
