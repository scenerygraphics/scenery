package graphics.scenery.numerics

import org.joml.Vector3f
import com.jogamp.opengl.math.Quaternion
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector4f

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
         * Returns a random [Vector2f], where all elements are in the
         * range [min]-[max].
         */
        @JvmStatic
        fun random2DVectorFromRange(min: Float, max: Float): Vector2f {
            return Vector2f(randomFromRange(min, max),
                randomFromRange(min, max))
        }


        /**
         * Returns a random [Vector3f], where all elements are in the
         * range [min]-[max].
         */
        @JvmStatic
        fun random3DVectorFromRange(min: Float, max: Float): Vector3f {
            return Vector3f(
                randomFromRange(min, max),
                randomFromRange(min, max),
                randomFromRange(min, max))
        }

        /**
         * Returns a random [Vector4f], where all elements are in the
         * range [min]-[max].
         */
        @JvmStatic
        fun random4DVectorFromRange(min: Float, max: Float): Vector4f {
            return Vector4f(
                randomFromRange(min, max),
                randomFromRange(min, max),
                randomFromRange(min, max),
                randomFromRange(min, max))
        }

        /**
         * Returns a random [Quaternion] generated from random Euler angles.
         */
        @JvmStatic
        fun randomQuaternion(): Quaternionf {
            val values = (0..2).map { randomFromRange(0.0f, 1.0f) }
            return Quaternionf().rotateXYZ(values[0], values[1], values[2])
        }
    }
}
