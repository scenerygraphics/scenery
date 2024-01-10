package graphics.scenery.attribute.buffers


import graphics.scenery.BufferUtils
import graphics.scenery.backends.UBO
import graphics.scenery.utils.lazyLogger
import net.imglib2.type.numeric.NumericType
import java.io.Serializable
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

interface Buffers : Serializable {

    var buffers : MutableMap<String, BufferDescription>
    var downloadRequests : MutableSet<String>

    var customVertexLayout : UBO?

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
    data class BufferDescription(var buffer: Buffer?, val type : BufferType, val usage: BufferUsage, val elements: Int = 0, val size : Int = 0, val inheritance: Boolean = false)

    /**
     * This function adds a custom layout buffer [name] to the [Buffers] attribute. [usage] refers to the memory usage/data flow of data from and to the GPU. [elements]
     * stands for the element count, and [stride] refers to the total byte length per element. The layout of the element is represented internally by a UBO.
     * Its layout can be manipulated by the lambda, using layout.add{}.
     */
    fun addCustom(name: String, usage: BufferUsage, elements: Int, stride: Int, inheritance: Boolean = false, block: (UBO, Buffer?) -> Unit) : BufferDescription {

        val layout = UBO()
        // TODO: use the correct VkPhysicalDeviceProperties.limits.minUniformBufferOffsetAlignment
        //  + make buffer padding a function (code double in VulkanBufferAllocation func fit)
        val minUboAlignment = 256
        var alignedSize = elements * stride
        if (minUboAlignment > 0) {
            alignedSize = (alignedSize + minUboAlignment - 1) and (minUboAlignment - 1).inv()
        }
        val buffer = if(!inheritance) { BufferUtils.allocateByte(alignedSize)} else { null }
        val description = BufferDescription(buffer, BufferType.Custom(layout), usage, elements, alignedSize, inheritance)
        // Is this correct for the buffer?
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

    /**
     * Adds a download request to buffer of [name], which will be consumed in the render loop. Contents should be available post-render
     */
    fun requestDownload(name: String) {
        val logger by lazyLogger()

        if(buffers.containsKey(name))
        {
            downloadRequests.add(name)
        } else {
            logger.error("Buffer {} not defined", name)
        }
    }

    /**
     * Used to define a custom vertex layout used inside the renderer.
     * Example:
     *      addCustomVertexLayout { layout ->
     *          layout.add("Position",  { Vector3f(0.0f)}, 0)
     *          layout.add("Normal",  { Vector3f(0.0f)}, 16)
     *          layout.add("UV",  { Vector2f(0.0f)},  32)
     *      }
     */
    fun addCustomVertexLayout(block: (UBO) -> Unit) {

        val layout = UBO()
        block(layout)
        customVertexLayout = layout
    }

    /**
     * Update the entry [layoutEntry] of buffer [name] at [index] with new [value].
     *
     */
    fun updateSSBOEntry(name : String, index : Int, layoutEntry : String = "", value : Any) {

        buffers[name]?.let {
            //check if index is within buffer (index < it?.elements)
            when (it.type) {
                is BufferType.Primitive<*> -> {
                }

                is BufferType.Custom -> {
                    val layout = it.type.layout
                    it.buffer?.let { buffer ->
                        (buffer.duplicate() as ByteBuffer).order(ByteOrder.LITTLE_ENDIAN).run {
                            layout.populate(this, index.toLong(), linkedMapOf(layoutEntry to { value }))
                            dirtySSBOs = true
                        }
                    }
                }
            }
        }
    }

    /**
     * Retrieve contents of buffer [name] at [index] in [layoutEntry]
     */
    fun getSSBOEntry(name : String, index : Int, layoutEntry : String = "") : Any? {
        val logger by lazyLogger()

        buffers[name]?.let {bufferDescription ->
            //check if index is within buffer (index < it?.elements)
            when (bufferDescription.type) {
                is BufferType.Primitive<*> -> {
                }

                is BufferType.Custom -> {
                    val layout = bufferDescription.type.layout
                    bufferDescription.buffer?.let { buffer ->
                        (buffer.duplicate() as ByteBuffer).order(ByteOrder.LITTLE_ENDIAN).run {
                            layout.memberSizeAndOffset(layoutEntry)?.let { sizeAndOffset ->
                                if(index + (sizeAndOffset.second ?: 0) + sizeAndOffset.first < bufferDescription.size)
                                    for(i in 0 until bufferDescription.size) {
                                        logger.info("{}", this.get(i))


                                        return this.getFloat(index + (sizeAndOffset.second ?: 0)) //cast this accordingly to the type (might be a vec, Int, float, boolean?)
                                    }
                            }
                            logger.warn("Buffer entry {} could not be retrieved from buffer {}", layoutEntry, name)
                        }
                    }
                }
            }
        }
        logger.warn("Could not get {} of buffer {}", layoutEntry, name)
        return null
    }
}
