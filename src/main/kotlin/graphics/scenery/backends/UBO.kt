package graphics.scenery.backends

import cleargl.GLMatrix
import cleargl.GLVector
import gnu.trove.map.hash.TIntObjectHashMap
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.math.max

/**
 * UBO base class, providing API-independent functionality for OpenGL and Vulkan.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class UBO {
    var name = ""
    protected var members = LinkedHashMap<String, () -> Any>()
    protected var memberOffsets = HashMap<String, Int>()
    protected val logger by LazyLogger()
    var hash: Int = 0
        private set
    var initialized: Boolean = false

    protected var sizeCached = -1

    companion object {
        var alignments = TIntObjectHashMap<Pair<Int, Int>>()
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    fun sizeOf(element: Any): Int {
        return when(element) {
            is GLVector -> element.toFloatArray().size
            is GLMatrix -> element.floatArray.size
            is Float, is java.lang.Float -> 4
            is Double, is java.lang.Double -> 8
            is Int, is Integer -> 4
            is Short, is java.lang.Short  -> 2
            is Boolean, is java.lang.Boolean -> 4
            else -> { logger.error("Don't know how to determine size of $element"); 0 }
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    fun Any.objectId(): Int {
        return when(this) {
            is GLVector -> 0
            is GLMatrix -> 1
            is Float, is java.lang.Float -> 2
            is Double, is java.lang.Double -> 3
            is Int, is Integer -> 4
            is Short, is java.lang.Short  -> 5
            is Boolean, is java.lang.Boolean -> 6
            else -> { logger.error("Don't know how to determine object ID of $this/${this.javaClass.simpleName}"); -1 }
        }
    }

    fun getSizeAndAlignment(element: Any): Pair<Int, Int> {
        // pack object id and size into one integer
        val key = (element.objectId() shl 16) or (sizeOf(element) and 0xffff)

        if(alignments.containsKey(key)) {
            return alignments.get(key)
        } else {
            val sa = when (element.javaClass) {
                GLMatrix::class.java -> {
                    Pair((element as GLMatrix).floatArray.size * 4, 4 * 4)
                }

                GLVector::class.java -> {
                    val v = element as GLVector
                    val size = v.toFloatArray().size
                    val alignment = when (size) {
                        2 -> 2
                        3 -> 4
                        4 -> 4
                        else -> 4
                    }

                    Pair(size * 4, alignment * 4)
                }

                java.lang.Float::class.java -> Pair(4, 4)
                Float::class.java -> Pair(4, 4)

                java.lang.Double::class.java -> Pair(8, 8)
                Double::class.java -> Pair(8, 8)

                Integer::class.java -> Pair(4, 4)
                Int::class.java -> Pair(4, 4)

                Short::class.java -> Pair(2, 2)

                java.lang.Boolean::class.java -> Pair(4, 4)
                Boolean::class.java -> Pair(4, 4)

                else -> {
                    logger.error("Unknown VulkanUBO member type: ${element.javaClass.simpleName}")
                    Pair(0, 0)
                }
            }

            alignments.put(key, sa)

            return sa
        }
    }

    fun getSize(): Int {
        val totalSize = if(sizeCached == -1) {
            val size = members.map {
                getSizeAndAlignment(it.value.invoke())
            }.fold(0) { current_position, (first, second) ->
                // next element should start at the position
                // required by it's alignment
                val remainder = current_position.rem(second)

                val new_position = if (remainder != 0) {
                    current_position + second - remainder + first
                } else {
                    current_position + first
                }

                new_position
            }

            sizeCached = size
            size
        } else {
            sizeCached
        }

        return totalSize
    }

    fun populate(data: ByteBuffer, offset: Long = -1L, elements: (LinkedHashMap<String, () -> Any>)? = null): Boolean {
        // no need to look further
        if(members.size == 0) {
            return false
        }

        if(offset != -1L) {
            data.position(offset.toInt())
        }

        val originalPos = data.position()
        var endPos = originalPos

        val oldHash = hash

        if(sizeCached > 0) {
            val newHash = getMembersHash(data.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(originalPos + sizeCached) as ByteBuffer)
            if(oldHash == newHash && elements == null) {
                data.position(originalPos + sizeCached)
                logger.trace("UBO members of {} have not changed, {} vs {}", this, hash, getMembersHash(data))
                return false
            }
        }

        logger.info("Hash changed $oldHash -> ${getMembersHash(data)}, $sizeCached, $elements")

        (elements ?: members).forEach {
            var pos = data.position()
            val value = it.value.invoke()

            val (size, alignment) = getSizeAndAlignment(value)

            if(logger.isTraceEnabled) {
                logger.trace("Populating {} of type {} size={} alignment={}", it.key, value.javaClass.simpleName, size, alignment)
            }

            if(memberOffsets[it.key] != null) {
                // position in buffer is known, use it
                if(logger.isTraceEnabled) {
                    logger.trace("{} goes to {}", it.key, memberOffsets[it.key]!!)
                }

                pos = (originalPos + memberOffsets[it.key]!!)
                data.position(pos)
            } else {
                // position in buffer is not explicitly known, advance based on size
                if (pos.rem(alignment) != 0) {
                    pos = pos + alignment - (pos.rem(alignment))
                    data.position(pos)
                }
            }

            when(value.javaClass) {

                GLMatrix::class.java -> {
                    (value as GLMatrix).push(data)
                }
                GLVector::class.java -> {
                    (value as GLVector).push(data)
                }

                java.lang.Float::class.java -> {
                    data.asFloatBuffer().put(0, value as Float)
                }
                Float::class.java -> {
                    data.asFloatBuffer().put(0, value as Float)
                }

                java.lang.Double::class.java -> {
                    data.asDoubleBuffer().put(0, value as Double)
                }
                Double::class.java -> {
                    data.asDoubleBuffer().put(0, value as Double)
                }

                Integer::class.java -> {
                    data.asIntBuffer().put(0, value as Int)
                }
                Integer::class.java -> {
                    data.asIntBuffer().put(0, value as Int)
                }
                Int::class.java -> {
                    data.asIntBuffer().put(0, value as Int)
                }

                java.lang.Short::class.java -> {
                    data.asShortBuffer().put(0, value as Short)
                }
                Short::class.java -> {
                    data.asShortBuffer().put(0, value as Short)
                }

                java.lang.Boolean::class.java -> {
                    data.asIntBuffer().put(0, value as Int)
                }
                Boolean::class.java -> {
                    data.asIntBuffer().put(0, value as Int)
                }
            }

            data.position(pos + size)
            endPos = max(pos + size, endPos)
        }

        data.position(endPos)

        sizeCached = data.position() - originalPos
        updateHash(data)

        logger.trace("UBO {} updated, {} -> {}", this, oldHash, hash)

        return true
    }

    fun add(name: String, value: () -> Any, offset: Int? = null) {
        val previous = members.put(name, value)

        offset?.let {
            memberOffsets.put(name, offset)
        }

        if(previous == null || previous.invoke().javaClass != value.invoke().javaClass) {
            // invalidate sizes
            sizeCached = -1
        }
    }

    fun members(): String {
        return members.keys.joinToString(", ")
    }

    fun membersAndContent(): String {
        return members.entries.joinToString { "${it.key} -> ${it.value.invoke()}, " }
    }

    fun memberCount(): Int {
        return members.size
    }

    fun perMemberHashes(): String {
        return members.map { "${it.key} -> ${it.key.hashCode()} ${it.value.invoke().hashCode()}" }.joinToString("\n")
    }

    protected fun getMembersHash(buffer: ByteBuffer): Int {
        return members.map { (it.key.hashCode() xor it.value.invoke().hashCode()).toLong() }
            .fold(31L) { acc, value -> acc + (value xor (value.ushr(32)))}.toInt() + MemoryUtil.memAddress(buffer).hashCode()
    }

    protected fun updateHash(buffer: ByteBuffer) {
        hash = getMembersHash(buffer)
    }

    fun get(name: String): (() -> Any)? {
        return members[name]
    }
}
