package graphics.scenery.tests.unit

import cleargl.GLVector
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import graphics.scenery.CatmullRomSpline
import graphics.scenery.numerics.Random

/**
 * This is the test class for the [CatmullRomSpline]
 *
 *@author Justin Bürger
 */
class CatmullRomSplineTests {
    private val logger by LazyLogger()

    @Test
    fun testCreation() {
        logger.info("This is the test for the CatmullRomSpline.")
        val x1 = Random.randomFromRange(-30f, -10f)
        val y1 = Random.randomFromRange(-30f, -10f)
        val z1 = Random.randomFromRange(-30f, -10f)
        val x2 = Random.randomFromRange(-10f, 20f)
        val y2 = Random.randomFromRange(-10f, 20f)
        val z2 = Random.randomFromRange(-10f, 20f)
        val x3 = Random.randomFromRange(20f, 30f)
        val y3 = Random.randomFromRange(20f, 30f)
        val z3 = Random.randomFromRange(20f, 30f)
        val x4 = Random.randomFromRange(30f, 100f)
        val y4 = Random.randomFromRange(30f, 100f)
        val z4 = Random.randomFromRange(30f, 100f)

        val controlPoints = arrayListOf(GLVector(x1, y1, z1), GLVector(x2, y2, z2), GLVector(x3, y3, z3),
        GLVector(x4, y4, z4))

        val curve = CatmullRomSpline(controlPoints)

        assertNotNull(curve)
        assertTrue(curve.catMullRomChain().size == 100)
    }

}
