package graphics.scenery.utils

import cleargl.GLVector

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Numerics {
    companion object {
        @JvmStatic
        fun randomFromRange(min: Float, max: Float): Float = min + (Math.random().toFloat() * ((max - min) + 1.0f))

        @JvmStatic
        fun randomVectorFromRange(dimension: Int, min: Float, max: Float): GLVector {
            return GLVector(*(1..dimension).map { randomFromRange(min, max) }.toFloatArray())
        }
    }
}
