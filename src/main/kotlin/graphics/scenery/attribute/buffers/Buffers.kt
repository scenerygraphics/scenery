package graphics.scenery.attribute.buffers


import graphics.scenery.BufferUtils
import graphics.scenery.backends.UBO
import net.imglib2.type.numeric.NumericType
import java.io.Serializable
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface Buffers : Serializable {

    var buffers : MutableMap<String, BufferDescription>

    var dirtySSBOs : Boolean

    // TODO: Redo this. Differentiate between read/write and ReadWrite // Input/Output + Downloadable(Y/N) should be enough
    // TODO: and if the buffer is GPU only or if it needs to be send back to the cpu (Upload/download or both)
    enum class BufferUsage {
        Upload,
        Download
    }

    /**
     * [usage] is currently a hashset to provide buffers to be both upload and download buffers -> maybe exclude this possibility to prevent
     * an increase in code in the renderer backend?
     */
    data class BufferDescription(var buffer: Buffer, val type : BufferType, val usage: BufferUsage, val elements: Int = 0, val size : Int = 0, val inheritance: Boolean = false)

    /**
     * This function adds a custom layout buffer [name] to the [Buffers] attribute. [usage] refers to the memory usage/data flow of data from and to the GPU. [elements]
     * stands for the element count, and [stride] refers to the total byte length per element. The layout of the element is represented internally by a UBO.
     * Its layout can be manipulated by the lambda, using layout.add{}.
     */
    fun addCustom(name: String, usage: BufferUsage, elements: Int, stride: Int, inheritance: Boolean = false, block: (UBO, Buffer) -> Unit) : BufferDescription {

        val layout = UBO()
        val totalSize = elements * stride
        // TODO: check if this can be done better (maybe with nullable buffer?)
        val buffer = if(!inheritance) { BufferUtils.allocateByte(totalSize)} else { BufferUtils.allocateByte(0) }
        val description = BufferDescription(buffer, BufferType.Custom(layout), usage, elements, totalSize, inheritance)
        block(layout, buffer)
        buffers[name] = description

        return description
    }

    // TODO: Get rid of primitive type -> no need for them. A primitive buffer is uploaded using a layout as well. Layout then just contains one entry of type.
    /**
     * This function adds a primitive layout buffer [name] to the [Buffers] attribute. [type] is a [NumericType] that will be filled into the buffer.
     * [elements] stands for the element count, and [stride] for the size in bytes of the type.
     */
    fun <T : NumericType<T>> addPrimitive(name: String, type : T, elements: Int, stride: Int = 0, inheritance: Boolean = false) : BufferDescription {

        val totalSize = elements * stride
        val buffer = BufferUtils.allocateByte(totalSize)
        val description = BufferDescription(buffer, BufferType.Primitive(type), BufferUsage.Upload, elements, totalSize, inheritance)
        buffers[name] = description

        return description
    }

    fun updateSSBOEntry(name : String, index : Int, layoutEntry : String = "", value : Any) {

        buffers[name]?.let {
            //check if index is within buffer (index < it?.elements)
            when (it.type) {
                is BufferType.Primitive<*> -> {
                }

                is BufferType.Custom -> {
                    val layout = it.type.layout
                    (it.buffer.duplicate() as ByteBuffer).order(ByteOrder.LITTLE_ENDIAN).run {
                        layout.populate(this, index.toLong(), linkedMapOf(layoutEntry to {value}))
                        dirtySSBOs = true
                    }
                }
            }
        }
    }
}
