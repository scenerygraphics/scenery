package graphics.scenery.backends.vulkan

import graphics.scenery.utils.lazyLogger
import org.lwjgl.vulkan.VK10
import java.util.concurrent.CopyOnWriteArrayList


/** Default [VulkanBufferPool] backing store size. */
const val basicBufferSize: Long = 1024*1024*128

/**
 * Represents a pool of [VulkanBuffer]s, from which [VulkanSuballocation]s can be made.
 * Each buffer pool resides on a [device] and has specific [usage] flags, e.g. for vertex
 * or texture storage.
 */
class VulkanBufferPool(
    val device: VulkanDevice,
    val usage: Int = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT or VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
    val bufferSize: Long = basicBufferSize,
    val properties: Int = VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
    var poolName : String
) {

    private val logger by lazyLogger()
    protected val backingStore = CopyOnWriteArrayList<VulkanBufferAllocation>()

    /**
     * Creates a new [VulkanSuballocation] of a given [size]. If the allocation cannot be made with
     * the current set of buffers in [backingStore], a new buffer will be added.
     */
    @Synchronized fun create(size: Int): VulkanSuballocation {
        val options = backingStore.filter { it.usage == usage && it.fit(size) != null }

        return if(options.isEmpty()) {
            logger.trace("Could not find space for allocation of {}, creating new buffer", size)
            var bufferSize = this.bufferSize
            while(bufferSize < size) {
                bufferSize *= 2
            }

            // increase size for new backing store members in case we already have a few,
            // to limit the number of necessary buffers
            if(bufferSize == this.bufferSize && backingStore.size > 4) {
                bufferSize *= 4
            }

            val vb = VulkanBuffer(device, bufferSize, usage, properties, true, name = "${poolName}_SubAllocatedBuffer")
            val alloc = VulkanBufferAllocation(usage, vb.allocatedSize, vb, vb.alignment.toInt())
            backingStore.add(alloc)
            logger.trace("Added new buffer of size {} to backing store", bufferSize)

            val suballocation = alloc.allocate(alloc.fit(size) ?: throw IllegalStateException("New allocation of ${vb.allocatedSize} cannot fit $size"))

            suballocation
        } else {
            val suballocation = options.first().fit(size) ?: throw IllegalStateException("Suballocation vanished")
            options.first().allocate(suballocation)

            suballocation
        }
    }

    /**
     * Creates a new [VulkanBuffer] of [size], backed by this [VulkanBufferPool].
     */
    fun createBuffer(size: Int, name : String = "BufferFromPool"): VulkanBuffer {
        return VulkanBuffer.fromPool(this, size.toLong(), name)
    }

    /**
     * Returns a string representation of this pool.
     */
    override fun toString(): String {
        return backingStore.mapIndexed { i, it ->
            "Backing store buffer $i: $it"
        }.joinToString("\n")
    }

    /**
     * Closes this buffer pool.
     */
    fun close() {
        backingStore.forEach { store ->
            store.buffer.close()
        }
    }
}

