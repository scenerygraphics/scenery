package graphics.scenery.tests.unit

import graphics.scenery.numerics.Random
import graphics.scenery.geometry.CatmullRomSpline
import graphics.scenery.geometry.curve.Helix
import graphics.scenery.geometry.curve.Shape
import graphics.scenery.geometry.curve.toShape
import graphics.scenery.proteins.PositionDirection
import graphics.scenery.utils.lazyLogger
import org.joml.Vector3f
import org.junit.Test
import kotlin.math.absoluteValue
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * This is the test class for the Helix class.
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */

class HelixTests {
    private val logger by lazyLogger()

    /**
     * Test that a null vector can never be a direction vector.
     */
    @Test
    fun testNullVector() {
        logger.info("Test whether the exception gets thrown when the direction is the null vector.")
        val line = PositionDirection(Random.random3DVectorFromRange(-10f, 10f), Vector3f(0f, 0f, 0f))
        val point1 = Random.random3DVectorFromRange( -30f, -10f)
        val point2 = Random.random3DVectorFromRange( -9f, 20f)
        val point3 = Random.random3DVectorFromRange( 21f, 30f)
        val point4 = Random.random3DVectorFromRange( 31f, 100f)
        val controlPoints = arrayListOf(point1, point2, point3, point4)
        val spline = CatmullRomSpline(controlPoints)
        val shape = ArrayList<Vector3f>()
        shape.add(Vector3f(0.3f, 0.3f, 0f))
        shape.add(Vector3f(0.3f, -0.3f, 0f))
        shape.add(Vector3f(-0.3f, -0.3f, 0f))

        assertFails { Helix(line, spline) { listOf( shape.toShape() ) } }
    }

    /**
     * Tests if the vertices of the baseShapes are properly aligned with the helix axis after the transformation.
     */
    @Test
    fun testVectorAxisAlignment() {
        logger.info("Test if the baseShapes are correctly aligned to the axis")
        val helixRectangle = listOf(Vector3f(5f, 5f, 0f), Vector3f(-5f, 5f, 0f),
                Vector3f(-5f, -5f, 0f), Vector3f(5f, -5f, 0f))
        val helixSplineControlPoints = ArrayList<Vector3f>(4*5)
        var i = 0
        for(j in 0 until 20) {
            helixRectangle.forEach {
                val vec = Vector3f(it.x(), it.y(), i.toFloat())
                helixSplineControlPoints.add(vec)
                i++
            }
        }
        val spline = CatmullRomSpline(helixSplineControlPoints, 20)
        fun baseShape(): Shape {
            val list = ArrayList<Vector3f>(4)
            list.add(Vector3f(0.5f, 0.5f, 0f))
            list.add(Vector3f(-0.5f, 0.5f, 0f))
            list.add(Vector3f(-0.5f, -0.5f, 0f))
            list.add(Vector3f(0.5f, -0.5f, 0f))
            return list.toShape()
        }
        val axis = PositionDirection(
            Random.random3DVectorFromRange(-10f, 10f),
            Random.random3DVectorFromRange(-10f, 10f).normalize()
        )
        val helix = Helix(axis, spline) { listOf( baseShape())}
        val vertices = helix.callPrivateFunc("calculateVertices")
        if(vertices is ArrayList<*>) {
            @Suppress("UNCHECKED_CAST")
            vertices.windowed(2, 1) {
                if(it[0] is Vector3f && it[1] is Vector3f) {
                    val p1 = it[0] as Vector3f
                    val p2 = it[1] as Vector3f
                    //The vector between two consecutive points of the rectangle
                    val v = Vector3f()
                    p2.sub(p1, v)
                    val cos = v.angleCos(axis.direction).absoluteValue
                    //The cos with the axis direction should be close to 0 (lines parallel to y axis) or 1 (x axis)
                    assertTrue { cos < 0.01f || (0.99f < cos && cos < 1.01f)}
                }
            }
        }
    }

    //Inline function to access private function in the RibbonDiagram
    private inline fun <reified T> T.callPrivateFunc(name: String, vararg args: Any?): Any? =
            T::class
                    .declaredMemberFunctions
                    .firstOrNull { it.name == name }
                    ?.apply { isAccessible = true }
                    ?.call(this, *args)
}
