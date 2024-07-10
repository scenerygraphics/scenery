package graphics.scenery.tests.unit

import graphics.scenery.geometry.curve.*
import graphics.scenery.geometry.UniformBSpline
import graphics.scenery.utils.lazyLogger
import org.joml.Vector3f
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Test for the [Curve] geometry
 *
 * @author Justin Buerger
 */
class CurveTests {
    private val logger by lazyLogger()

    /**
     * Tests if frenet frames along a spline are relatively equidistant and do not overlap
     */
    @Test
    fun equidistantNonIdenticalFrenetFrames() {
        logger.info("testing that frenet frames along a spline are relatively equidistant and do not overlap")
        val points = ArrayList<Vector3f>()
        points.add(Vector3f(-8f, -9f, -9f))
        points.add(Vector3f(-7f, -5f, -7f))
        points.add(Vector3f(-5f, -5f, -5f))
        points.add(Vector3f(-4f, -2f, -3f))
        points.add(Vector3f(-2f, -3f, -4f))
        points.add(Vector3f(-1f, -1f, -1f))
        points.add(Vector3f(0f, 0f, 0f))
        points.add(Vector3f(2f, 1f, 0f))

        fun shapeGenerator(splineVerticesCount: Int): ArrayList<ArrayList<Vector3f>> {
            val shapeList = ArrayList<ArrayList<Vector3f>>(splineVerticesCount)
            val splineVerticesCountThird = splineVerticesCount/3
            val splineVerticesCountTwoThirds = splineVerticesCount*2/3
            for (i in 0 until splineVerticesCountThird) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.3f, 0.3f, 0f))
                list.add(Vector3f(0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, -0.3f, 0f))
                shapeList.add(list)
            }
            for(i in splineVerticesCountThird until splineVerticesCountTwoThirds) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.3f, 0.3f, 0f))
                list.add(Vector3f(0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, 0.3f, 0f))
                shapeList.add(list)
            }
            for(i in splineVerticesCountTwoThirds until splineVerticesCount) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.3f, 0.3f, 0f))
                list.add(Vector3f(0.3f, -0.3f, 0f))
                list.add(Vector3f(0f, -0.5f, 0f))
                list.add(Vector3f(-0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, 0.3f, 0f))
                list.add(Vector3f(0f, 0.5f, 0f))
                shapeList.add(list)
            }
            return shapeList
        }

        val bSpline = UniformBSpline(points, 10)

        val frenetFrames = FrenetCurve.computeFrenetFrames(bSpline)

        val distances = ArrayList<Float>()
        val sameFrame = ArrayList<Boolean>()

        frenetFrames.windowed(2, 1) {
            val distanceBetweenFrames = abs(it[1].translation.distance(it[0].translation))
            distances.add(distanceBetweenFrames)
            sameFrame.add((distanceBetweenFrames == 0f))
        }

        assertTrue(sameFrame.none { it })
        assertTrue(distances.none { 0.0001f > it && it > 1f})
    }

}
