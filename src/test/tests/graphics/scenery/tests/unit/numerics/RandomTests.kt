package graphics.scenery.tests.unit.numerics

import graphics.scenery.numerics.Random
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.toFloatArray
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [Random] class.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

class RandomTests {
    private val logger by LazyLogger()

    @Test
    fun testRandomFromRange() {
        logger.info("Testing generation random numbers from range ...")
        val min = kotlin.random.Random.nextDouble(0.0, 0.9).toFloat()
        val max = kotlin.random.Random.nextDouble(min.toDouble(), 1.0).toFloat()

        val fromRange = Random.randomFromRange(min, max)
        assertTrue(fromRange >= min, "Random value was expected to be larger than $min, but is $fromRange")
        assertTrue(fromRange <= max, "Random value was expected to be smaller than $max, but is $fromRange")
    }

    @Test
    fun testRandom2DVectorFromRange() {
        logger.info("Testing generation random vectors from range ...")
        val min = kotlin.random.Random.nextDouble(0.0, 0.9).toFloat()
        val max = kotlin.random.Random.nextDouble(min.toDouble(), 1.0).toFloat()

        val vectors = (0 until 32).map {
            Random.random2DVectorFromRange(min, max)
        }

        vectors.forEach { v ->
            assertTrue(v.toFloatArray().all { it >= min }, "Random value was expected to be larger than $min, but is smaller")
            assertTrue(v.toFloatArray().all { it <= max }, "Random value was expected to be smaller than $max, but is larger")
        }
    }

    @Test
    fun testRandom3DVectorFromRange() {
        logger.info("Testing generation random vectors from range ...")
        val min = kotlin.random.Random.nextDouble(0.0, 0.9).toFloat()
        val max = kotlin.random.Random.nextDouble(min.toDouble(), 1.0).toFloat()

        val vectors = (0 until 32).map {
            Random.random3DVectorFromRange(min, max)
        }

        vectors.forEach { v ->
            assertTrue(v.toFloatArray().all { it >= min }, "Random value was expected to be larger than $min, but is smaller")
            assertTrue(v.toFloatArray().all { it <= max }, "Random value was expected to be smaller than $max, but is larger")
        }
    }

    @Test
    fun testRandom4DVectorFromRange() {
        logger.info("Testing generation random vectors from range ...")
        val min = kotlin.random.Random.nextDouble(0.0, 0.9).toFloat()
        val max = kotlin.random.Random.nextDouble(min.toDouble(), 1.0).toFloat()

        val vectors = (0 until 32).map {
            Random.random4DVectorFromRange(min, max)
        }

        vectors.forEach { v ->
            assertTrue(v.toFloatArray().all { it >= min }, "Random value was expected to be larger than $min, but is smaller")
            assertTrue(v.toFloatArray().all { it <= max }, "Random value was expected to be smaller than $max, but is larger")
        }
    }



    @Test
    fun testRandomQuaternion() {
        logger.info("Testing random quaternion generation ...")
        val q = Random.randomQuaternion()

        assertTrue(q.lengthSquared() in (1.0f - 0.0001f)..(1.0f + 0.0001f), "Quaternion should be normalized")
    }
}
