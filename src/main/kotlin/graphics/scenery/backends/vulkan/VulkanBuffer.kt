package graphics.scenery.backends.vulkan

import glm_.L
import graphics.scenery.utils.LazyLogger
import kool.Adr
import kool.cap
import kool.free
import kool.pos
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memAlloc
import vkk.*
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Vulkan buffer class, creates a buffer residing on [device], with a [size] and a defined [usage].
 * The buffer may have [requestedMemoryProperties]. The buffer can be created with a [suballocation]
 * from a [VulkanBufferPool] -- if created in this way it'll not manage its own memory, but leave that
 * to the pool.
 *
 * @param[wantAligned] - whether the buffer should be aligned
 */
open class VulkanBuffer(val device: VulkanDevice,
                        var size: VkDeviceSize,
                        val usage: VkBufferUsageFlags,
                        val requestedMemoryProperties: VkMemoryPropertyFlags = VkMemoryProperty.DEVICE_LOCAL_BIT.i,
                        val wantAligned: Boolean = true,
                        var suballocation: VulkanSuballocation? = null) : AutoCloseable {

    private val logger by LazyLogger()
    private var currentPosition = 0L
    private var currentAddress: Adr = NULL

    /** Buffer alignment, 256 bytes by default (Vulkan standard) */
    var alignment = VkDeviceSize(256)
        private set
    /** Memory pointer */
    var memory = VkDeviceMemory(NULL)
        private set
    /** Buffer to the Vulkan pointer struct */
    var vulkanBuffer = VkBuffer(NULL)
        private set
    /** Final allocated size of the buffer, might be different from the requested size, due to alignment. */
    var allocatedSize = VkDeviceSize(0)
        private set
    /** Buffer offset. */
    var bufferOffset = VkDeviceSize(0)
        private set

    private var mapped = false
    private var bufferReallocNeeded: Boolean = false

    /** Staging buffer, providing host memory */
    var stagingBuffer: ByteBuffer = memAlloc(size.i)
        private set

    val vkDev get() = device.vulkanDevice

    init {
        if (suballocation == null) {
            val b = allocateVulkanBuffer(size, wantAligned)

            this.memory = b.memory
            this.vulkanBuffer = b.buffer
            this.allocatedSize = b.size
            this.alignment = b.alignment
        } else {
            suballocation?.let { sa ->
                this.memory = sa.buffer.memory
                this.vulkanBuffer = sa.buffer.vulkanBuffer
                this.allocatedSize = VkDeviceSize(sa.size.L)
                this.alignment = sa.buffer.alignment

                this.bufferOffset = VkDeviceSize(sa.offset.L)
            }
        }
    }

    private data class RawBuffer(val buffer: VkBuffer, val memory: VkDeviceMemory, val size: VkDeviceSize, val alignment: VkDeviceSize)

    private fun allocateVulkanBuffer(size: VkDeviceSize, wantAligned: Boolean): RawBuffer {

        val bufferInfo = vk.BufferCreateInfo {
            usage = this@VulkanBuffer.usage
            this.size = size
        }
        val buffer = vkDev createBuffer bufferInfo
        val reqs = vkDev getBufferMemoryRequirements buffer

        val actualSize = when {
            wantAligned -> when (reqs.size % reqs.alignment) {
                VkDeviceSize(0L) -> reqs.size
                else -> reqs.size + reqs.alignment - (reqs.size % reqs.alignment)
            }
            else -> reqs.size
        }

        val allocInfo = vk.MemoryAllocateInfo {
            allocationSize = actualSize
            memoryTypeIndex = device.getMemoryType(reqs.memoryTypeBits(), requestedMemoryProperties).first()
        }

        val memory = vkDev allocateMemory allocInfo
        vkDev.bindBufferMemory(buffer, memory)

        return RawBuffer(buffer, memory, actualSize, reqs.alignment)
    }

    /**
     * Resizes the backing buffer to [newSize], which is 1.5x the original size by default,
     * and returns the staging buffer.
     */
    @Synchronized
    fun resize(newSize: Int = (size.L * 1.5f).roundToInt()): ByteBuffer {
        if (mapped) {
            unmap()
        }

        logger.debug("Before resize: ${stagingBuffer.remaining()} ${stagingBuffer.capacity()}")
        stagingBuffer = MemoryUtil.memRealloc(stagingBuffer, newSize) ?: throw IllegalStateException("Could not resize buffer")
        size = VkDeviceSize(newSize.L)
        bufferReallocNeeded = true

        return stagingBuffer
    }

    /**
     * Resizes the actual Vulkan buffer. Called upon copying the staging buffer to the Vulkan buffer.
     */
    @Synchronized
    protected fun resizeLazy() {
        if (!bufferReallocNeeded) {
            return
        }
        unmap()

        destroyVulkanBuffer()
        val b = allocateVulkanBuffer(VkDeviceSize(stagingBuffer.cap.L), wantAligned)

        this.memory = b.memory
        this.vulkanBuffer = b.buffer
        this.allocatedSize = b.size
        this.alignment = b.alignment

        bufferReallocNeeded = false
    }

    /**
     * Advances this buffer to the next possible aligned position,
     * override the buffer's default alignment by setting [align] to
     * the desired value. Returns the new position.
     */
    fun advance(align: VkDeviceSize = this.alignment): VkDeviceSize {
        val pos = stagingBuffer.pos
        val rem = pos % align

        if (rem.i != 0) {
            val newpos = pos + align.i - rem.i
            stagingBuffer.pos = newpos
        }

        return VkDeviceSize(stagingBuffer.pos.L)
    }

    /**
     * Resets the staging buffer such that it can be again filled
     * from the beginning.
     */
    fun reset() {
        stagingBuffer.pos = 0
        stagingBuffer.lim = size.i
        currentPosition = 0L
    }

    /**
     * Copies data from the [ByteBuffer] [data] directly to the device memory.
     */
    fun copyFrom(data: ByteBuffer) {
        resizeLazy()

        vkDev.mappedMemory(memory, bufferOffset, size) { dest ->
            data copyTo dest
        }
    }

    /**
     * Copies the contents of the device buffer to [dest].
     */
    fun copyTo(dest: ByteBuffer) {
        vkDev.mappedMemory(memory, bufferOffset, size) { src ->
            dest copyFrom src
        }
    }

    /**
     * Maps this buffer
     */
    fun map(): Adr {
        resizeLazy()

        val adr = vkDev.mapMemory(memory, bufferOffset, size)

        currentAddress = adr
        mapped = true
        return adr
    }

    /**
     * Maps this buffer it wasn't mapped before, and rewinds it.
     */
    fun mapIfUnmapped(): Adr {
        resizeLazy()

        if (currentAddress != NULL && mapped)
            return currentAddress

        return map()
    }

    /**
     * Unmaps this buffer.
     */
    fun unmap() {
        mapped = false
        vkDev unmapMemory memory
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
        return vulkanBuffer.L != NULL && memory.L != NULL
    }

    private fun destroyVulkanBuffer() {
        if (memory.L == NULL || vulkanBuffer.L == NULL) {
            return
        }

        vkDev freeMemory memory
        memory = VkDeviceMemory(NULL)

        vkDev destroyBuffer vulkanBuffer
        vulkanBuffer = VkBuffer(NULL)
    }

    /**
     * Closes this buffer, freeing all allocated resources on host and device.
     */
    override fun close() {
        if (memory.L == NULL || vulkanBuffer.L == NULL) {
            return
        }

        if (suballocation != null) {
            logger.debug("Marking suballocation as free")
            suballocation?.free = true
            return
        }

        logger.trace("Closing buffer $this ...")

        if (mapped) {
            unmap()
        }

        stagingBuffer.free()

        vkDev freeMemory memory
        memory = VkDeviceMemory(NULL)

        vkDev destroyBuffer vulkanBuffer
        vulkanBuffer = VkBuffer(NULL)
    }

    /**
     * Factory methods for [VulkanBuffer].
     */
    companion object {
        /**
         * Creates a new VulkanBuffer of [size] that has it's memory managed by a [VulkanBufferPool]
         * given by [pool].
         */
        fun fromPool(pool: VulkanBufferPool, size: VkDeviceSize): VulkanBuffer {
            val suballocation = pool.create(size)
            return VulkanBuffer(pool.device, VkDeviceSize(suballocation.size.L),
                usage = pool.usage, suballocation = suballocation)
        }
    }
}
