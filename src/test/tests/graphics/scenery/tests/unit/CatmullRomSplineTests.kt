package graphics.scenery.tests.unit

import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import graphics.scenery.CatmullRomSpline
import graphics.scenery.numerics.Random
import graphics.scenery.utils.extensions.minus

/**
 * This is the test class for the [CatmullRomSpline]
 *
 *@author Justin Bürger
 */
class CatmullRomSplineTests {
    private val logger by LazyLogger()

    /**
     * Tests if the curve object has actually the number of points defined in the class.
     */
    @Test
    fun testLength() {
        logger.info("This is the test for the Length of the chain.")
        val point1 = Random.random3DVectorFromRange(-30f, -10f)
        val point2 = Random.random3DVectorFromRange(-9f, 20f)
        val point3 = Random.random3DVectorFromRange(21f, 30f)
        val point4 = Random.random3DVectorFromRange(31f, 100f)

        val controlPoints = arrayListOf(point1, point2, point3, point4)

        val curve = CatmullRomSpline(controlPoints)
        assertNotNull(curve)
        /*
        The computation of the Catmull Rom Spline delivers an additional point if the
        distance between the point1 and point2 is small relative to point2 and point3
         */
        assertTrue(curve.catMullRomChain().size == 100 || curve.catMullRomChain().size == 101)
    }

    /**
     * This test verifies that the points created by the Catmull Rom Spline are sufficiently equidistant.
     */
    @Test
    fun testChain() {
        logger.info("This is the test for the Length of the chain.")
        val point1 = Random.random3DVectorFromRange(-30f, -10f)
        val point2 = Random.random3DVectorFromRange(-9f, 20f)
        val point3 = Random.random3DVectorFromRange(21f, 30f)
        val point4 = Random.random3DVectorFromRange(31f, 100f)

        val controlPoints = arrayListOf(point1, point2, point3, point4)

        val curve = CatmullRomSpline(controlPoints)
        val chain = curve.catMullRomChain()
        val i = Random.randomFromRange(1f, 98f).toInt()
        val distance = chain[i].minus(chain[i+1]).length()
        val distanceDifferences = chain.windowed(2, 1) {
            it[0].minus(it[1]).length().minus(distance) }.toList()
        println(distanceDifferences)
        assertTrue { distanceDifferences.filter { it < 0.1 } == distanceDifferences }
    }

}
