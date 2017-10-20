package graphics.scenery.utils

import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion

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

        @JvmStatic
        fun randomQuaternion(): Quaternion {
            val values = (0..2).map { randomFromRange(0.0f, 1.0f) }
            return Quaternion().setFromEuler(values[0], values[1], values[2])
        }
    }
}
