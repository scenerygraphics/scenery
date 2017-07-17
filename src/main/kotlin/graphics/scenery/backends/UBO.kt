package graphics.scenery.backends

import cleargl.GLMatrix
import cleargl.GLVector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * UBO base class, providing API-independent functionality for OpenGL and Vulkan.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class UBO {
    var name = ""
    var members = LinkedHashMap<String, () -> Any>()
    var logger: Logger = LoggerFactory.getLogger("UBO")

    protected var sizeCached = 0

    companion object alignmentsCache {
        var alignments = HashMap<Class<*>, Pair<Int, Int>>()
    }

    fun getSizeAndAlignment(element: Any): Pair<Int, Int> {
        if(alignments.containsKey(element.javaClass)) {
            return alignments[element.javaClass]!!
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

            alignments.put(element.javaClass, sa)

            return sa
        }
    }

    fun getSize(): Int {
        val totalSize = if(sizeCached == 0) {
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

    fun populate(data: ByteBuffer, offset: Long = 0) {
        members.forEach {
            var pos = data.position()
            val value = it.value.invoke()
            val (size, alignment) = getSizeAndAlignment(value)

            if(pos.rem(alignment) != 0) {
                pos = pos + alignment - (pos.rem(alignment))
                data.position(pos)
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
        }
    }
}
