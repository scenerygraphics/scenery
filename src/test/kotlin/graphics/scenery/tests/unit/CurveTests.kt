package graphics.scenery.tests.unit

import graphics.scenery.geometry.UniformBSpline
import graphics.scenery.numerics.Random
import graphics.scenery.utils.lazyLogger
import org.junit.Test

class CurveTests {
    private val logger by lazyLogger()

    @Test
    fun equidistantFrenetFrames() {
        val point1 = Random.random3DVectorFromRange( -30f, -10f)
        val point2 = Random.random3DVectorFromRange( -9f, 20f)
        val point3 = Random.random3DVectorFromRange( 21f, 30f)
        val point4 = Random.random3DVectorFromRange( 31f, 100f)
        val controlPoints = arrayListOf(point1, point2, point3, point4)
        val spline = UniformBSpline(controlPoints)

    }

}
