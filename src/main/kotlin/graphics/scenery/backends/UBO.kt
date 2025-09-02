package graphics.scenery.backends

import gnu.trove.map.hash.TIntObjectHashMap
import graphics.scenery.utils.lazyLogger
import org.joml.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.math.max

/**
 * UBO base class, providing API-independent uniform buffer serialisation
 * functionality for both OpenGL and Vulkan.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class UBO {
    /** Name of this UBO */
    var name = ""

    internal var members = LinkedHashMap<String, () -> Any>()
    internal var memberOffsets = HashMap<String, Int>()
    protected val logger by lazyLogger()

    /** Hash value of all the members, gets updated by [populate()] */
    var hash: Int = 0
        private set

    /** Cached size of the UBO, -1 if the UBO has not been populated yet. */
    var sizeCached = -1
        protected set

    companion object {
        /** Cache for alignment data inside buffers */
        internal var alignments = TIntObjectHashMap<Pair<Int, Int>>()
    }

    /**
     * Returns the size of [element] inside a uniform buffer.
     */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    protected fun sizeOf(element: Any): Int {
        return when(element) {
            is Vector2f, is Vector2i -> 2
            is Vector3f, is Vector3i -> 3
            is Vector4f, is Vector4i -> 4
            is Matrix4f -> 4 * 4
            is Float, is java.lang.Float -> 4
            is Double, is java.lang.Double -> 8
            is Int, is Integer -> 4
            is Short, is java.lang.Short  -> 2
            is Boolean, is java.lang.Boolean -> 4
            is Enum<*> -> 4
            is FloatArray -> element.size * 4
            is IntArray -> element.size * 4
            else -> { logger.error("Don't know how to determine size of $element"); 0 }
        }
    }

    /**
     * Translates an object to an integer ID for more efficient storage in [alignments].
     */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    protected fun Any.objectId(): Int {
        return when(this) {
            is Float, is java.lang.Float -> 2
            is Double, is java.lang.Double -> 3
            is Int, is Integer -> 4
            is Short, is java.lang.Short  -> 5
            is Boolean, is java.lang.Boolean -> 6
            is Enum<*> -> 7

            is Vector2f -> 8
            is Vector3f -> 9
            is Vector4f -> 10

            is Vector2i -> 11
            is Vector3i -> 12
            is Vector4i -> 13

            is Matrix4f -> 14

            is FloatArray -> 15
            is IntArray -> 16
            else -> { logger.error("Don't know how to determine object ID of $this/${this.javaClass.simpleName}"); -1 }
        }
    }

    /**
     * Returns the size occupied and alignment required for [element] inside a uniform buffer.
     * Pair layout is <size, alignment>.
     */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    fun getSizeAndAlignment(element: Any): Pair<Int, Int> {
        // pack object id and size into one integer
        val key = (element.objectId() shl 16) or (sizeOf(element) and 0xffff)

        if(alignments.containsKey(key)) {
            return alignments.get(key)
        } else {
            val sa = when (element) {
                is Matrix4f -> Pair(4 * 4 * 4, 4 * 4)

                is Vector2f, is Vector2i -> Pair(2*4, 2*4)
                is Vector3f, is Vector3i -> Pair(3*4, 4*4)
                is Vector4f, is Vector4i -> Pair(4*4, 4*4)

                is Float -> Pair(4, 4)
                is Double -> Pair(8, 8)
                is Integer -> Pair(4, 4)
                is Int -> Pair(4, 4)
                is Short -> Pair(2, 2)
                is Boolean -> Pair(4, 4)
                is Enum<*> -> Pair(4, 4)

                is FloatArray -> Pair(16*element.size, 4*4)
                is IntArray -> Pair(16*element.size, 4*4)

                else -> {
                    logger.error("Unknown VulkanUBO member type: ${element.javaClass.simpleName}")
                    Pair(0, 0)
                }
            }

            alignments.put(key, sa)

            return sa
        }
    }

    /**
     * Returns the total size in bytes required to store the contents of this UBO in a uniform buffer.
     */
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

    /**
     * Populates the [ByteBuffer] [data] with the members of this UBO, subject to the determined
     * sizes and alignments. A buffer [offset] can be given, as well as a list of [elements] that
     * would override the UBO's members. This routine checks if an actual buffer update is required,
     * and if not, will just set the buffer to the cached position. Otherwise it will serialise all
     * the members into [data].
     *
     * Returns true if [data] has been updated, and false if not.
     */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
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

        if(sizeCached > 0 && elements == null) {
            // the members hash is also based on the memory address of the buffer, which is calculated at the
            // end of the routine and therefore dependent on the final buffer position.
            val newHash = getMembersHash(data.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(originalPos + sizeCached) as ByteBuffer)
            if(oldHash == newHash) {
                data.position(originalPos + sizeCached)
                logger.trace("UBO members of {} have not changed, {} vs {}", this, hash, newHash)

                // indicates the buffer will not be updated, but only forwarded to the cached position
                return false
            }
        }

        // iterate over members, or over elements, if given
        (elements ?: members).forEach {
            var pos = data.position()
            val value = it.value.invoke()

            val (size, alignment) = getSizeAndAlignment(value)

            if(logger.isTraceEnabled) {
                logger.trace("Populating {} of type {} size={} alignment={}", it.key, value.javaClass.simpleName, size, alignment)
            }

            val memberOffset = memberOffsets[it.key]
            if(memberOffset != null) {
                // position in buffer is known, use it
                if(logger.isTraceEnabled) {
                    logger.trace("{} goes to {}", it.key, memberOffset)
                }

                pos = (originalPos + memberOffset)
                data.position(pos)
            } else {
                // position in buffer is not explicitly known, advance based on size
                if (pos.rem(alignment) != 0) {
                    pos = pos + alignment - (pos.rem(alignment))
                    data.position(pos)
                }
            }

            when (value) {
                is Matrix4f -> value.get(data)

                is Vector2i -> value.get(data)
                is Vector3i -> value.get(data)
                is Vector4i -> value.get(data)

                is Vector2f -> value.get(data)
                is Vector3f -> value.get(data)
                is Vector4f -> value.get(data)

                is Float -> data.asFloatBuffer().put(0, value)
                is Double -> data.asDoubleBuffer().put(0, value)
                is Integer -> data.asIntBuffer().put(0, value.toInt())
                is Int -> data.asIntBuffer().put(0, value)
                is Short -> data.asShortBuffer().put(0, value)
                is Boolean -> data.asIntBuffer().put(0, value.toInt())
                is Enum<*> -> data.asIntBuffer().put(0, value.ordinal)

                is FloatArray -> if (value.size % 4 == 0) {
                    data.asFloatBuffer().put(value)
                } else {
                    // std140 rules demand 16 byte stride for arrays
                    val fb = data.asFloatBuffer()
                    val padding = floatArrayOf(0.0f, 0.0f, 0.0f)
                    value.forEach { f ->
                        fb.put(f)
                        fb.put(padding)
                    }
                }

                is IntArray -> if (value.size % 4 == 0) {
                    data.asIntBuffer().put(value)
                } else {
                    // std140 rules demand 16 byte stride for arrays
                    val ib = data.asIntBuffer()
                    val padding = intArrayOf(0, 0, 0)
                    value.forEach { i ->
                        ib.put(i)
                        ib.put(padding)
                    }
                }
            }

            data.position(pos + size)
            endPos = max(pos + size, endPos)
        }

        data.position(endPos)

        sizeCached = data.position() - originalPos
        if(elements == null) {
            updateHash(data)
        }

        logger.trace("UBO {} updated, {} -> {}", this, oldHash, hash)

        // indicates the buffer has been updated
        return true
    }

    /**
     * Adds a member with [name] to this UBO. [value] is given as a lambda
     * that will return the actual value when invoked. An optional [offset] can be
     * given, otherwise it will be calculated automatically.
     *
     * Invalidates the UBO's hash if no previous member is associated with [name],
     * or if a previous member already bears [name], but has another type than the
     * invocation of [value].
     */
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

    /**
     * Adds the member only if its missing.
     */
    fun addIfMissing(name: String, value: () -> Any, offset: Int? = null) {
        if(!members.containsKey(name)) {
            add(name, value, offset)
        }
    }

    /**
     * Returns the members of the UBO as string.
     */
    fun members(): String {
        return members.keys.joinToString(", ")
    }

    /**
     * Returns the members of the UBO and their values as string.
     */
    fun membersAndContent(): String {
        return members.entries.joinToString { "${it.key} -> ${it.value.invoke()}" }
    }

    /**
     * Returns the number of members of this UBO.
     */
    fun memberCount(): Int {
        return members.size
    }

    /**
     * Return a list of all member sizes and offsets of this UBO
     */
    fun membersSizesAndOffsets()  : LinkedList<Pair<Int, Int>> {
        val list  =  LinkedList<Pair<Int, Int>>()
        members.forEach {
            memberOffsets.get(it.key)?.let { it1 ->
                list.add(sizeOf(it.value.invoke()) to it1)
            }
        }
        return list
    }

    /**
     * Return the size and offset of member [name] if it exists in this UBO.
     */
    fun memberSizeAndOffset(name : String) : Pair<Int, Int?>? {
         return when(members.containsKey(name)) {
            true -> sizeOf(members[name]!!.invoke()) to memberOffsets[name]
            else -> null
        }
    }

    /**
     * For debugging purposes. Returns the hashes of all members as string.
     */
    @Suppress("unused")
    internal fun perMemberHashes(): String {
        return members.map { "${it.key} -> ${it.key.hashCode()} ${it.value.invoke().hashCode()}" }.joinToString("\n")
    }

    /**
     * Returns the hash value of all current members.
     *
     * Takes into consideration the member's name and _invoked_ value, as well as the
     * buffer's memory address to discern buffer switches the UBO is oblivious to.
     *
     * In case the member's _invoked_ value is an IntArray or FloatArray, contentHashCode()
     * is used instead of hashCode(), such that simple array reallocation without content
     * changes do not affect the hashing outcome.
     */
    protected fun getMembersHash(buffer: ByteBuffer): Int {
        return members.map { (it.key.hashCode() xor it.value.invoke().hash()).toLong() }
            .fold(31L) { acc, value -> acc + (value xor (value.ushr(32)))}.toInt() + MemoryUtil.memAddress(buffer).hashCode()
    }

    private fun Any.hash(): Int = when(this) {
        is FloatArray -> this.contentHashCode()
        is IntArray -> this.contentHashCode()
        else -> this.hashCode()
    }

    /**
     * Updates the currently stored member hash.
     */
    protected fun updateHash(buffer: ByteBuffer) {
        hash = getMembersHash(buffer)
    }

    /**
     * Returns the lambda associated with member of [name], or null
     * if it does not exist.
     */
    fun get(name: String): (() -> Any)? {
        return members[name]
    }

    private fun Boolean.toInt(): Int {
        return if(this) { 1 } else { 0 }
    }
}

