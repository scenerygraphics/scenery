package graphics.scenery.attribute.buffers


import graphics.scenery.BufferUtils
import graphics.scenery.backends.UBO
import net.imglib2.type.numeric.NumericType
import org.joml.Vector4f
import java.io.Serializable
import java.nio.Buffer
import java.nio.ByteBuffer

interface Buffers : Serializable {

    var buffers : MutableMap<String, BufferDescription>
    var dirtySSBOs : Boolean

    enum class BufferUsage {
        Upload,
        Download
    }

    /**
     * [usage] is currently a hashset to provide buffers to be both upload and download buffers -> maybe exclude this possibility to prevent
     * an increase in code in the renderer backend?
     */
    data class BufferDescription(val buffer: Buffer, val type : BufferType, val usage: HashSet<BufferUsage>, val size : Int)


    /**
     * This function adds a custom layout buffer [name] to the [Buffers] attribute. [usage] refers to the memory usage/data flow of data from and to the GPU. [elements]
     * stands for the element count, and [stride] refers to the total byte length per element. The layout of the element is represented internally by a UBO.
     * Its layout can be manipulated by the lambda, using layout.add{}.
     */
    fun addCustom(name: String, usage: HashSet<BufferUsage>, elements: Int, stride: Int, block: () -> Unit) : BufferDescription {

        val layout = UBO()
        val totalSize = elements * stride
        val buffer = BufferUtils.allocateByte(totalSize)
        val description = BufferDescription(buffer, BufferType.Custom(layout), usage, totalSize)
        buffers[name] = description

        return description
    }

    /**
     * This function adds a primitive layout buffer [name] to the [Buffers] attribute. [type] is a [NumericType] that will be filled into the buffer.
     * [elements] stands for the element count, and [stride] for the size in bytes of the type.
     */
    fun <T : NumericType<T>> addPrimitive(name: String, type : T, elements: Int, stride: Int = 0) : BufferDescription {

        val totalSize = elements * stride
        val buffer = BufferUtils.allocateByte(totalSize)
        val description = BufferDescription(buffer, BufferType.Primitive(type), hashSetOf(BufferUsage.Upload), totalSize)
        buffers[name] = description

        return description
    }
}
