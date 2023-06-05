package graphics.scenery.tests.unit.numerics

import graphics.scenery.numerics.OpenSimplexNoise
import graphics.scenery.utils.lazyLogger
import org.junit.Assert
import org.junit.Test
import java.util.zip.GZIPInputStream
import kotlin.random.Random

/**
 * Tests for [OpenSimplexNoise].
 * Samples taken from https://github.com/ojrac/opensimplex-go
 * In this test suite, evaluations from our OpenSimplexNoise Kotlin implementation with default seed 0
 * are compared to the Java implementation's results.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class OpenSimplexNoiseTests {
    private val logger by lazyLogger()

    @Test
    fun test1DNoise() {
        logger.info("Testing 1D OpenSimplex noise...")

        // TODO: Add sample-based test for 1D noise
        OpenSimplexNoise(Random.nextLong())
    }

    @Test
    fun test2DNoise() {
        logger.info("Testing 2D OpenSimplex noise with ${samples2D.size} samples ...")
        val noise = OpenSimplexNoise(0L)

        samples2D.forEach { s ->
            Assert.assertEquals(s[2], noise.random2D(s[0], s[1]), delta)
        }
    }

    @Test
    fun test3DNoise() {
        logger.info("Testing 3D OpenSimplex noise with ${samples3D.size} samples ...")

        val noise = OpenSimplexNoise(0L)
        samples3D.forEach { s ->
            Assert.assertEquals(s[3], noise.random3D(s[0], s[1], s[2]), delta)
        }
    }

    @Test
    fun test4DNoise() {
        logger.info("Testing 4D OpenSimplex noise with ${samples4D.size} samples ...")

        val noise = OpenSimplexNoise(0L)
        samples4D.forEach { s ->
            Assert.assertEquals(s[4], noise.random4D(s[0], s[1], s[2], s[3]), delta)
        }
    }

    companion object {
        val samples2D = ArrayList<List<Float>>()
        val samples3D = ArrayList<List<Float>>()
        val samples4D = ArrayList<List<Float>>()

        // floating point delta
        const val delta = 0.0001f

        init {
            val samplesStream = GZIPInputStream(OpenSimplexNoiseTests::class.java.getResourceAsStream("samples.json.gz"))
            samplesStream.reader().readLines().forEach { line ->
                val numbers = line.replace("[", "").replace("]", "").split(",").map { it.toFloat() }
                when(numbers.size) {
                    3 -> samples2D.add(numbers)
                    4 -> samples3D.add(numbers)
                    5 -> samples4D.add(numbers)
                }
            }
        }
    }
}
