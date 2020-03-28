package graphics.scenery.tests.unit

import cleargl.GLVector
import graphics.scenery.UniformBSpline
import graphics.scenery.numerics.Random
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * This is the test for the [UniformBSpline].
 *
 * @author Justin Bürger
 */
class UniformBSplineTests {
    private val logger by LazyLogger()

    /**
     * Tests if the Uniform B-Spline object has actually the number of points defined in the class.
     */
    @Test
    fun testLength() {
        logger.info("This is the test for the length of the chain.")
        val point1 = Random.randomVectorFromRange(3, -30f, -10f)
        val point2 = Random.randomVectorFromRange(3, -9f, 20f)
        val point3 = Random.randomVectorFromRange(3, 21f, 30f)
        val point4 = Random.randomVectorFromRange(3, 31f, 100f)

        val controlPoints = arrayListOf(point1, point2, point3, point4)

        val bSpline = UniformBSpline(controlPoints)
        val spline = bSpline.splinePoints()

        assertNotNull(spline)
        assertEquals(spline.size, ((controlPoints.size-3)*100 +1))
    }

    /**
     * This test verifies that the points created by the Uniform B-Spline are sufficiently equidistant.
     */
    @Test
    fun testChain() {
        logger.info("This is the test for the Length of the chain.")
        val point1 = Random.randomVectorFromRange(3, -30f, -10f)
        val point2 = Random.randomVectorFromRange(3, -9f, 20f)
        val point3 = Random.randomVectorFromRange(3, 21f, 30f)
        val point4 = Random.randomVectorFromRange(3, 31f, 100f)

        val controlPoints = arrayListOf(point1, point2, point3, point4)

        val curve = UniformBSpline(controlPoints)
        val chain = curve.splinePoints()
        val i = Random.randomFromRange(1f, 99f).toInt()
        val distance = chain[i].minus(chain[i+1]).length2()
        val distanceDifferences = chain.windowed(2, 1) {
            it[0].minus(it[1]).length2().minus(distance) }.toList()
        println(distanceDifferences)
        assertTrue { distanceDifferences.filter { it < 0.5 } == distanceDifferences }
    }

    /**
     * Tests what happens if the Uniform B-Spline gets created with not enough information, meaning
     * either an empty list, a list with only the same points, and a list with less then four points.
     */
    @Test
    fun invalidControlPoints() {
        logger.info("Tests Uniform BSpline with invalid control points.")
        val samePointList = ArrayList<GLVector>(10)
        val point = GLVector(1f, 1f, 1f)
        for(i in 0..9) {
            samePointList.add(point)
        }
        val samePointSpline = UniformBSpline(samePointList)
        assertFails{ samePointSpline.splinePoints() }

        val emptyList = ArrayList<GLVector>()
        val emptySpline = UniformBSpline(emptyList)
        assertTrue(emptySpline.splinePoints().isEmpty())

        val notEnoughList = ArrayList<GLVector>()
        val j = Random.randomFromRange(1f, 2f).roundToInt()
        for(i in 0..j) {
            val vector = Random.randomVectorFromRange(3, 0f, 5f)
            notEnoughList.add(vector)
        }
        val notEnoughSpline = UniformBSpline(notEnoughList)
        assertTrue(notEnoughSpline.splinePoints().isEmpty())
    }
}
