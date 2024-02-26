package graphics.scenery.numerics

import org.joml.Vector3f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector4f
import kotlin.math.PI
import kotlin.random.Random

/**
 * Helper class to generate random numbers.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Random {
    companion object {
        /** Seed to use for random number generation */
        var seed = System.getProperty("scenery.RandomSeed")?.toLong() ?: Random.nextLong()

        /** Random number generator instance */
        var rng = Random(seed)

        /**
         * Reseeds the PRNG with the new [seed]. If the seed is null, the value given in the
         * system property `scenery.RandomSeed` will be used.
         */
        @JvmStatic fun reseed(seed: Long? = null) {
            this.seed = seed ?: (System.getProperty("scenery.RandomSeed")?.toLong() ?: Random.nextLong())
            rng = Random(this.seed)
        }

        /**
         * Returns a random float from the range [min]-[max].
         */
        @JvmStatic
        fun randomFromRange(min: Float, max: Float): Float = (max - min) * rng.nextFloat() + min

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
            val values = (0..2).map { randomFromRange(-PI.toFloat()/2.0f, PI.toFloat()/2.0f) }
            return Quaternionf().rotateXYZ(values[0], values[1], values[2]).normalize()
        }
    }
}
