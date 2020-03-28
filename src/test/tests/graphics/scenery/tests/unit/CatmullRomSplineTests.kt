package graphics.scenery.tests.unit

import cleargl.GLVector
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import graphics.scenery.CatmullRomSpline
import graphics.scenery.numerics.Random
<<<<<<< HEAD
import kotlin.math.roundToInt
=======
import kotlin.math.roundToInt
>>>>>>> Extended the UniformBSplineTests, CurveTests, and CatmullRomSplineTests. Also implemented some Exceptions for empty controlpoints in UniformBSpline.

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
        assertTrue(curve.splinePoints().size == 100 || curve.splinePoints().size == 101)
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
        val chain = curve.splinePoints()
        val i = Random.randomFromRange(1f, 98f).toInt()
        val distance = chain[i].minus(chain[i+1]).length()
        val distanceDifferences = chain.windowed(2, 1) {
<<<<<<< HEAD
            it[0].minus(it[1]).length2().minus(distance) }.toList()
=======
            it[0].minus(it[1]).length2().minus(distance) }.toList()
>>>>>>> Added UniformBSplineTests and documentation for the Spline.
        assertTrue { distanceDifferences.filter { it < 0.1 } == distanceDifferences }
    }

    /**
     * Tests what happens if the Catmull-Rom-Spline gets created with not enough information, meaning
     * either an empty list, a list with only the same points, and a list with less then four points.
     */
    @Test
    fun invalidControlPoints() {
        logger.info("Tests CatmullRomSpline with invalid control points.")
        val samePointList = ArrayList<GLVector>(10)
        val point = GLVector(1f, 1f, 1f)
        for(i in 0..9) {
            samePointList.add(point)
        }
        val samePointSpline = CatmullRomSpline(samePointList)
        assertTrue(samePointSpline.splinePoints().isEmpty())

        val emptyList = ArrayList<GLVector>()
        val emptySpline = CatmullRomSpline(emptyList)
        assertTrue(emptySpline.splinePoints().isEmpty())

        val notEnoughList = ArrayList<GLVector>()
        val j = Random.randomFromRange(1f, 2f).roundToInt()
        for(i in 0..j) {
            val vector = Random.randomVectorFromRange(3, 0f, 5f)
            notEnoughList.add(vector)
        }
        val notEnoughSpline = CatmullRomSpline(notEnoughList)
        assertTrue(notEnoughSpline.splinePoints().isEmpty())
    }
}
