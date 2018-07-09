package graphics.scenery.backends.vulkan

import graphics.scenery.utils.LazyLogger

typealias VulkanBufferUsage = Int
/**
 * Zips consecutive items of an Iterable, with the end items signalled by null.
 */
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

/**
 * Represents an allocation in a [VulkanBufferPool]'s backing store. This class does most
 * of the work, such as keeping track of all [VulkanBufferPool]'s [VulkanSuballocation]s.
 */
class VulkanBufferAllocation(val usage: VulkanBufferUsage,
                             val size: Long,
                             val buffer: VulkanBuffer,
                             val alignment: Int,
                             private val suballocations: ArrayList<VulkanSuballocation> = ArrayList<VulkanSuballocation>()) {
    private val logger by LazyLogger()

    fun allocate(suballocation: VulkanSuballocation): VulkanSuballocation {
        suballocations.add(suballocation)
        logger.trace("Added suballocation at {} with size {} ({} total allocations)", suballocation.offset, suballocation.size, suballocations.size)
        return suballocation
    }

    data class FreeSpace(val left: VulkanSuballocation?, val right: VulkanSuballocation?)

    private fun FreeSpace.getFreeSpace(): Int {
        return when {
            left == null && right != null -> right.offset
            left != null && right != null -> right.offset - (left.offset + left.size)
            left != null && right == null -> Int.MAX_VALUE
            left == null && right == null -> Int.MAX_VALUE
            else -> throw IllegalStateException("Can't calculate free space for $left/$right")
        }
    }

    fun fit(size: Int): VulkanSuballocation? {
        suballocations.removeAll { s -> s.free  }
        logger.trace("Trying to fit {} with {} pre-existing suballocs", size, suballocations.size)

        val candidates: MutableList<FreeSpace> = when (suballocations.size) {
            0 -> mutableListOf()
            1 -> mutableListOf(FreeSpace(null, suballocations.first()))
            else -> mutableListOf()
        }
        candidates.addAll(suballocations.zipWithNextNullable { left, right -> FreeSpace(left, right) })
        candidates.sortBy { it.getFreeSpace() - size }

        val spot = candidates.firstOrNull { it.getFreeSpace() > size && it.getFreeSpace() > alignment }

        if (spot == null) {
            logger.trace("Could not find space for suballocation of {}", size)
            return null
        } else {
            if(logger.isTraceEnabled) {
                logger.trace("Allocation candidates: ${candidates.filter { it.getFreeSpace() >= size }.joinToString(", ") { "L=${it.left}/R=${it.right} free=${it.getFreeSpace()}" }}")
            }

            var offset = with(spot) {
                when {
                    left == null && right != null -> right.offset + right.size
                    left != null && right != null -> left.offset + left.size
                    left != null && right == null -> left.offset + left.size
                    left == null && right == null -> 0
                    else -> throw IllegalStateException("Can't calculate offset space for $left/$right")
                }
            }

            if(offset.rem(alignment) != 0) {
                offset = offset + alignment - (offset.rem(alignment))
            }

            if(offset + size >= buffer.allocatedSize) {
                logger.trace("Allocation at {} of {} would not fit buffer of size {}", offset, size, buffer.allocatedSize)
                return null
            }

            logger.trace("New suballocation at {} between {} and {} with {} bytes", offset, spot.left, spot.right, size)
            return VulkanSuballocation(offset, size, buffer)
        }
    }

    /** Returns a string representation of this allocation, along with its [suballocations]. */
    override fun toString(): String {
        return "Allocations relating to $buffer of $size: ${suballocations.joinToString(",") { it.toString() }}"
    }
}
