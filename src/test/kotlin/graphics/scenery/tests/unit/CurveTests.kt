package graphics.scenery.tests.unit

import graphics.scenery.geometry.CatmullRomSpline
import graphics.scenery.geometry.Curve
import graphics.scenery.utils.LazyLogger
import junit.framework.Assert.assertEquals
import org.joml.Vector3f
import org.junit.Test
import kotlin.test.assertFails

/**
 * Unit Tests for the curve.
 *
 * @author Justin Buerger, burger@mpi-cbg.de
 */
class CurveTests {
    private val logger by LazyLogger()

    @Test
    fun testPartitionAtSplinePointLevel() {
        val points = ArrayList<Vector3f>()
        points.add(Vector3f(-8f, -9f, -9f))
        points.add(Vector3f(-7f, -5f, -7f))
        points.add(Vector3f(-5f, -5f, -5f))
        points.add(Vector3f(-4f, -2f, -3f))
        points.add(Vector3f(-2f, -3f, -4f))
        points.add(Vector3f(-1f, -1f, -1f))
        points.add(Vector3f(0f, 0f, 0f))
        points.add(Vector3f(2f, 1f, 0f))

        fun triangle(splineVerticesCount: Int): ArrayList<ArrayList<Vector3f>> {
            val shapeList = ArrayList<ArrayList<Vector3f>>(splineVerticesCount)
            for (i in 0 until splineVerticesCount) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.03f, 0.03f, 0f))
                list.add(Vector3f(0.03f, -0.03f, 0f))
                list.add(Vector3f(-0.03f, -0.03f, 0f))
                shapeList.add(list)
            }
            return shapeList
        }

        val catmullRom = CatmullRomSpline(points)
        val splineSize = catmullRom.splinePoints().size

        val rightCurve = Curve(catmullRom, false, true) { triangle(splineSize) }
        assertEquals(rightCurve.children.size, splineSize)

    }
}
