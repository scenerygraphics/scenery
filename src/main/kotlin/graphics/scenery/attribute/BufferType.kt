package graphics.scenery.attribute

import graphics.scenery.backends.UBO
import net.imglib2.type.numeric.NumericType

/**
 *
 */

sealed class BufferType {

    data class Primitive<T : NumericType<T>>(var type : T) : BufferType()
    data class Custom(var contents : UBO, var size : Int) : BufferType()
}
