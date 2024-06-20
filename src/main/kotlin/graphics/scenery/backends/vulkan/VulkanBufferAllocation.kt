package graphics.scenery.backends.vulkan

import graphics.scenery.utils.lazyLogger

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
    private val logger by lazyLogger()

    /**
     * Adds a new [suballocation].
     */
    fun allocate(suballocation: VulkanSuballocation): VulkanSuballocation {
        suballocations.add(suballocation)
        logger.trace("Added suballocation at {} with size {} ({} total allocations)", suballocation.offset, suballocation.size, suballocations.size)
        if(logger.isTraceEnabled) {
            logger.trace(this.toString())
        }
        return suballocation
    }

    /** Data class to contain free space regions between two [VulkanSuballocation]s */
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

    private fun findFreeSpaceCandidate(size: Int): FreeSpace? {
        val candidates: MutableList<FreeSpace> = when (suballocations.size) {
            0 -> mutableListOf()
            1 -> mutableListOf(FreeSpace(null, suballocations.first()))
            else -> mutableListOf()
        }
        candidates.addAll(suballocations.sortedBy { it.offset }.zipWithNextNullable { left, right -> FreeSpace(left, right) })
        candidates.sortBy { it.getFreeSpace() - size }

        if (logger.isTraceEnabled) {
            logger.trace("Allocation candidates: ${candidates.filter { it.getFreeSpace() >= size }.joinToString(", ") { "L=${it.left}/R=${it.right} free=${it.getFreeSpace()}" }}")
        }

        return candidates.firstOrNull { it.getFreeSpace() > size && it.getFreeSpace() > alignment }
    }

    /**
     * Tries to fit a new suballocation of [size] with the current suballocations. Returns
     * a new possible suballocation if feasible, and null otherwise.
     */
    fun fit(size: Int): VulkanSuballocation? {
        suballocations.removeAll { s -> s.free  }
        logger.trace("Trying to fit {} with {} pre-existing suballocs", size, suballocations.size)

        // TODO: use the correct VkPhysicalDeviceProperties.limits.minUniformBufferOffsetAlignment
        val minUboAlignment = 256
        var alignedSize = size
        if (minUboAlignment > 0) {
            alignedSize = (alignedSize + minUboAlignment - 1) and (minUboAlignment - 1).inv()
        }
        val spot = findFreeSpaceCandidate(alignedSize)

        if (spot == null) {
            logger.trace("Could not find space for suballocation of {}", alignedSize)
            return null
        }

        var offset = when {
            spot.left == null && spot.right != null -> spot.right.offset + spot.right.size
            spot.left != null && spot.right != null -> spot.left.offset + spot.left.size
            spot.left != null && spot.right == null -> spot.left.offset + spot.left.size
            spot.left == null && spot.right == null -> 0
            else -> throw IllegalStateException("Can't calculate offset space for ${spot.left}/${spot.right}")
        }

        // shift offset in case it would be unaligned
        if (offset.rem(alignment) != 0) {
            offset = offset + alignment - (offset.rem(alignment))
        }

        // check if offset + size of the new suballocation would exceed the buffer size
        if (offset + alignedSize >= buffer.allocatedSize) {
            logger.trace("Allocation at {} of {} would not fit buffer of size {}", offset, alignedSize, buffer.allocatedSize)
            return null
        }

        logger.trace("New suballocation at {} between {} and {} with {} bytes", offset, spot.left, spot.right, size)
        return VulkanSuballocation(offset, alignedSize, buffer)
    }

    /** Returns a string representation of this allocation, along with its [suballocations]. */
    override fun toString(): String {
        return "Allocations relating to $buffer ($size bytes):\n${suballocations.joinToString("\n") { " * $it" }}"
    }
}
