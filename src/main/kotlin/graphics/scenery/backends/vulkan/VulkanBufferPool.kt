package graphics.scenery.backends.vulkan

import graphics.scenery.utils.LazyLogger
import org.lwjgl.vulkan.VK10
import java.nio.ByteBuffer

typealias VulkanBufferProperties = Int

inline fun <T, R> Iterable<T>.zipWithNextNullable(transform: (a: T?, b: T?) -> R): List<R> {
    val iterator = iterator()
    if (!iterator.hasNext()) return listOf(transform(null, null))
    val result = mutableListOf<R>()
    var current = iterator.next()
    while (iterator.hasNext()) {
        val next = iterator.next()
        result.add(transform(current, next))
        current = next
    }
    result.add(transform(current, null))
    return result
}

class VulkanBufferAllocation(val properties: VulkanBufferProperties,
                                  val size: Long,
                                  val buffer: VulkanBuffer,
                                  val alignment: Int,
                                  val suballocations: ArrayList<VulkanSuballocation> = ArrayList<VulkanSuballocation>()) {
    private val logger by LazyLogger()

    fun allocate(suballocation: VulkanSuballocation): VulkanSuballocation {
        suballocations.add(suballocation)
        return suballocation
    }

    data class FreeSpace(val left: VulkanSuballocation?, val right: VulkanSuballocation?)

    fun FreeSpace.getFreeSpace(): Int {
        return when {
            left == null && right != null -> right.offset
            left != null && right != null -> right.offset - (left.offset + left.size)
            left != null && right == null -> Int.MAX_VALUE
            left == null && right == null -> Int.MAX_VALUE
            else -> throw IllegalStateException("Can't calculate free space for $left/$right")
        }
    }

    fun fit(size: Int): VulkanSuballocation? {
        logger.info("Trying to fit $size with ${suballocations.size} pre-existing suballocs")

        val candidates: MutableList<FreeSpace> = when (suballocations.size) {
            0 -> mutableListOf()
            1 -> mutableListOf(FreeSpace(null, suballocations.first()))
            else -> mutableListOf()
        }
        candidates.addAll(suballocations.zipWithNextNullable { left, right -> FreeSpace(left, right) })
        candidates.sortBy { it.getFreeSpace() - size }

        val spot = candidates.filter { it.getFreeSpace() > 0 }.firstOrNull()

        if (spot == null) {
            logger.info("Could not find space for suballocation of $size")
            return null
        } else {
            logger.info("Allocation candidates: ${candidates.joinToString(", ") { "${it.left}/${it.right} free=${it.getFreeSpace()}" }}")

            var offset = with(spot) {
                when {
                    left == null && right != null -> right.offset + right.size
                    left != null && right != null -> left.offset + left.size
                    left != null && right == null -> left.offset + left.size
                    left == null && right == null -> 0
                    else -> throw IllegalStateException("Can't calculate free space for $left/$right")
                }
            }

            if(offset.rem(alignment) != 0) {
                offset = offset + alignment - (offset.rem(alignment))
            }

            logger.info("New suballocation at $offset with $size bytes")
            return VulkanSuballocation(offset, size, buffer)
        }
    }
}

class VulkanSuballocation(var offset: Int, var size: Int, var buffer: VulkanBuffer) {
    fun getBuffer(): ByteBuffer {
        val b = buffer.stagingBuffer.duplicate()
        b.position(offset)
        b.limit(offset + size)

        return b
    }
}

const val basicBufferSize: Long = 1024*1024*32
class VulkanBufferPool(val device: VulkanDevice, val usage: Int = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT or VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT) {

    private val logger by LazyLogger()
    protected val backingStore = ArrayList<VulkanBufferAllocation>(10)

    init {

    }

    fun create(size: Int): VulkanSuballocation {
        val options = backingStore.filter { it.properties == usage && it.fit(size) != null }

        return if(options.isEmpty()) {
            logger.info("Could not find space for allocation of $size, creating new buffer")
            var bufferSize = basicBufferSize
            while(bufferSize < size) {
                bufferSize *= 2
            }

            val vb = VulkanBuffer(device, bufferSize, usage, VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, true)
            val alloc = VulkanBufferAllocation(usage, vb.allocatedSize, vb, vb.alignment.toInt())
            backingStore.add(alloc)
            logger.info("Added new buffer of size $bufferSize to backing store")

            val suballocation = alloc.allocate(alloc.fit(size) ?: throw IllegalStateException("New allocation of ${vb.allocatedSize} cannot fit $size"))

            suballocation
        } else {
            logger.info("Found free spot for allocation of $size, reserving...")
            val suballocation = options.first().fit(size) ?: throw IllegalStateException("Suballocation vanished")
            options.first().allocate(suballocation)

            suballocation
        }
    }
}

