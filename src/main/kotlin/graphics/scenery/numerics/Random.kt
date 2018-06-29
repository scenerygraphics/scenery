package graphics.scenery.numerics

import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion

/**
 * Helper class to generate random numbers.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Random {
    companion object {
        /**
         * Returns a random float from the range [min]-[max].
         */
        @JvmStatic
        fun randomFromRange(min: Float, max: Float): Float = (max - min)*Math.random().toFloat() + min

        /**
         * Returns a random [GLVector] with given [dimension], where all elements are in the
         * range [min]-[max].
         */
        @JvmStatic
        fun randomVectorFromRange(dimension: Int, min: Float, max: Float): GLVector {
            return GLVector(*(1..dimension).map { randomFromRange(min, max) }.toFloatArray())
        }

        /**
         * Returns a random [Quaternion] generated from random Euler angles.
         */
        @JvmStatic
        fun randomQuaternion(): Quaternion {
            val values = (0..2).map { randomFromRange(0.0f, 1.0f) }
            return Quaternion().setFromEuler(values[0], values[1], values[2])
        }
    }
}
