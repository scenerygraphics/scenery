package graphics.scenery.attribute.buffers

import graphics.scenery.backends.UBO
import net.imglib2.type.numeric.NumericType

/**
 * This type describes the layout of the buffer it is attached to.
 * [Primitive] is used for simple buffers that just store an array of primitive types.
 * [Custom] Is used for SSBOs (ShaderStorageBuffer), which can be composed out of more complex data. Its [layout], being represented as an UBO then describes
 * the structure of the attributes of one buffer element.
 */
sealed class BufferType {

    data class Primitive<T : NumericType<T>>(var type : T) : BufferType()
    data class Custom(var layout : UBO) : BufferType()
}
