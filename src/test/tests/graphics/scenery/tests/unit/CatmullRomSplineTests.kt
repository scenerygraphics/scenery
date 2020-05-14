package graphics.scenery.tests.unit

import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import graphics.scenery.CatmullRomSpline
import graphics.scenery.numerics.Random
import org.joml.Vector3f
import kotlin.collections.ArrayList
import kotlin.math.roundToInt


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
        val distance = chain[i].distance(chain[i+1])
        val distanceDifferences = chain.windowed(2, 1) {
            it[0].distance(it[1]).minus(distance) }.toList()
        /* The spline is drawn between point2 and point3. The biggest possible difference
         * between these points is roughly 70 units. We also have to take into account the rounding of the
         * spline. A conservative estimate, given the ranges, would be that of a half circle. That gives us
         * a curve length of totally 70*Pi/2 = 110 units. Our spline consist of 100 points, therefore, the
         * distance between spline points should not be bigger than 1,1 units.
         */
        assertTrue { distanceDifferences.filter { it < 1.1 } == distanceDifferences }
    }

    /**
     * Tests what happens if the Catmull-Rom-Spline gets created with not enough information, meaning
     * either an empty list, a list with only the same points, and a list with less then four points.
     */
    @Test
    fun invalidControlPoints() {
        logger.info("Tests CatmullRomSpline with invalid control points.")
        val samePointList = ArrayList<Vector3f>(10)
        val point = Vector3f(1f, 1f, 1f)
        for(i in 0..9) {
            samePointList.add(point)
        }
        val samePointSpline = CatmullRomSpline(samePointList)
        assertTrue(samePointSpline.splinePoints().isEmpty())

        val emptyList = ArrayList<Vector3f>()
        val emptySpline = CatmullRomSpline(emptyList)
        assertTrue(emptySpline.splinePoints().isEmpty())

        val notEnoughList = ArrayList<Vector3f>()
        val j = Random.randomFromRange(1f, 2f).roundToInt()
        for(i in 0..j) {
            val vector = Random.random3DVectorFromRange(0f, 5f)
            notEnoughList.add(vector)
        }
        val notEnoughSpline = CatmullRomSpline(notEnoughList)
        assertTrue(notEnoughSpline.splinePoints().isEmpty())
    }

    /**
     * Since the ranges between points are somewhat arbitrary – due to the fact, that the
     * spline was originally developed to visualize proteins (which are stored in 3D coordinates
     * in the same order of magnitude). Hence, this test verifies if the calculation is still
     * correct for bigger values.
     */
    @Test
    fun testLengthBigRanges() {
        logger.info("This is the test for the Length of the chain.")
        val point1 = Random.random3DVectorFromRange(-300f, -100f)
        val point2 = Random.random3DVectorFromRange(-90f, 20f)
        val point3 = Random.random3DVectorFromRange(210f, 300f)
        val point4 = Random.random3DVectorFromRange(310f, 1000f)

        val controlPoints = arrayListOf(point1, point2, point3, point4)

        val curve = CatmullRomSpline(controlPoints)
        assertNotNull(curve)
        /*
        The computation of the Catmull Rom Spline delivers an additional point if the
        distance between the point1 and point2 is small relative to point2 and point3
         */
        assertTrue(curve.splinePoints().size == 100 || curve.splinePoints().size == 101)
    }
}
