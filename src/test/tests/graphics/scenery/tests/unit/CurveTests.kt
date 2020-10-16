package graphics.scenery.tests.unit

import org.joml.*
import graphics.scenery.numerics.Random
import graphics.scenery.*
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import org.lwjgl.BufferUtils
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * This is the test class for the [Curve]
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class CurveTests {
    private val logger by LazyLogger()

    /**
     * Tests if the vectors are normalized and if the bitangent and normal vector are becoming null.
     */
    @Test
    fun testCurve() {
        logger.info("This is the test for the Curve.")
        val point1 = Random.random3DVectorFromRange( -30f, -10f)
        val point2 = Random.random3DVectorFromRange( -9f, 20f)
        val point3 = Random.random3DVectorFromRange( 21f, 30f)
        val point4 = Random.random3DVectorFromRange( 31f, 100f)

        val controlPoints = arrayListOf(point1, point2, point3, point4)

        val curve = CatmullRomSpline(controlPoints)

        fun triangle(splineVerticesCount: Int): ArrayList<ArrayList<Vector3f>> {
            val shapeList = ArrayList<ArrayList<Vector3f>>(splineVerticesCount)
            for (i in 0 until splineVerticesCount) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.3f, 0.3f, 0f))
                list.add(Vector3f(0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, -0.3f, 0f))
                shapeList.add(list)
            }
            return shapeList
        }

        val geometry = Curve(curve) { triangle(curve.splinePoints().size) }
        val frenetFrames = geometry.computeFrenetFrames(geometry.getCurve())

        assertEquals(curve.splinePoints(), geometry.getCurve())
        assertNotNull(frenetFrames.forEach { it.normal })
        assertNotNull(frenetFrames.forEach{ it.binormal })
        assertEquals(frenetFrames.filter { it.binormal.length() < 1.001f && it.binormal.length() > 0.999f },
                frenetFrames)
        assertEquals(frenetFrames.filter { it.normal.length() < 1.001f && it.normal.length() > 0.999f },
                frenetFrames)
        assertEquals(frenetFrames.filter { it.tangent.length() < 1.001f && it.tangent.length() > 0.999f },
                frenetFrames)
    }

    /**
     * Tests if the number of subCurves is correct.
     */
    @Test
    fun testNumberChildren() {
        logger.info("Tests if the number of subCurves is correct.")
        val point1 = Random.random3DVectorFromRange(-30f, -10f)
        val point2 = Random.random3DVectorFromRange(-9f, 20f)
        val point3 = Random.random3DVectorFromRange(21f, 30f)
        val point4 = Random.random3DVectorFromRange(31f, 100f)

        val controlPoints = arrayListOf(point1, point2, point3, point4)

        val spline = UniformBSpline(controlPoints)

        fun shapeGenerator(splineVerticesCount: Int): ArrayList<ArrayList<Vector3f>> {
            val shapeList = ArrayList<ArrayList<Vector3f>>(splineVerticesCount)
            val splineVerticesCountThird = splineVerticesCount / 3
            val splineVerticesCountTwoThirds = splineVerticesCount * 2 / 3
            for (i in 0 until splineVerticesCountThird) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.3f, 0.3f, 0f))
                list.add(Vector3f(0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, -0.3f, 0f))
                shapeList.add(list)
            }
            for (i in splineVerticesCountThird until splineVerticesCountTwoThirds) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.3f, 0.3f, 0f))
                list.add(Vector3f(0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, 0.3f, 0f))
                shapeList.add(list)
            }
            for (i in splineVerticesCountTwoThirds until splineVerticesCount) {
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
        val curve = Curve(spline) { shapeGenerator(spline.splinePoints().size) }
        assertTrue { curve.children.size == 3 }
    }
    /**
     * Verifies the curve fails when the there are more baseShapes than splinePoints or vice versa.
     */
    @Test
    fun testTooMuchShapeOrPoints() {
        logger.info("Verifies the curve fails when the there are more baseShapes than splinePoints or vice versa.")
        val point1 = Random.random3DVectorFromRange( -30f, -10f)
        val point2 = Random.random3DVectorFromRange( -9f, 20f)
        val point3 = Random.random3DVectorFromRange( 21f, 30f)
        val point4 = Random.random3DVectorFromRange( 31f, 100f)
        val point5 = Random.random3DVectorFromRange(101f, 122f)

        val controlPoints = arrayListOf(point1, point2, point3, point4)

        val spline = CatmullRomSpline(controlPoints)

        val controlPoints2 = arrayListOf(point1, point2, point3, point4, point5)

        val spline2 = CatmullRomSpline(controlPoints2)

        fun shapeGenerator(splineVerticesCount: Int): ArrayList<ArrayList<Vector3f>> {
            val shapeList = ArrayList<ArrayList<Vector3f>>(splineVerticesCount)
            val splineVerticesCountThird = splineVerticesCount / 3
            val splineVerticesCountTwoThirds = splineVerticesCount * 2 / 3
            for (i in 0 until splineVerticesCountThird) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.3f, 0.3f, 0f))
                list.add(Vector3f(0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, -0.3f, 0f))
                shapeList.add(list)
            }
            for (i in splineVerticesCountThird until splineVerticesCountTwoThirds) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.3f, 0.3f, 0f))
                list.add(Vector3f(0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, 0.3f, 0f))
                shapeList.add(list)
            }
            for (i in splineVerticesCountTwoThirds..splineVerticesCount) {
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
        assertFails { Curve(spline) { shapeGenerator(spline.splinePoints().size) } }
        assertFails { Curve(spline2) { shapeGenerator(spline2.splinePoints().size) } }
    }

    /**
     * Tests if the curve works properly even with an empty spline.
     */
    @Test
    fun testEmptySpline() {
        logger.info("Tests the curve with an empty spline")
        val emptyList = ArrayList<Vector3f>()
        val spline = UniformBSpline(emptyList)
        fun triangle(splineVerticesCount: Int): ArrayList<ArrayList<Vector3f>> {
            val shapeList = ArrayList<ArrayList<Vector3f>>(splineVerticesCount)
            for (i in 0 until splineVerticesCount) {
                val list = ArrayList<Vector3f>()
                list.add(Vector3f(0.3f, 0.3f, 0f))
                list.add(Vector3f(0.3f, -0.3f, 0f))
                list.add(Vector3f(-0.3f, -0.3f, 0f))
                shapeList.add(list)
            }
            return shapeList
        }
        val emptyFloatBuffer = BufferUtils.createFloatBuffer(0)
        val curve = Curve(spline) { triangle(spline.controlPoints().size) }
        assertEquals(curve.vertices, emptyFloatBuffer)
    }

    /**
     * Tests if the algorithm for the cover of the curve works for a polygon with 16 points.
     */
    @Test
    fun testBigPolygon() {
        logger.info("Tests if the algorithm for the cover of the curve works for a polygon with 16 points.")

        val sixteenPoints = ArrayList<Vector3f>(16)
        sixteenPoints.add(Vector3f(1f, 0f, 0f))
        sixteenPoints.add(Vector3f(0.9f, 0.45f, 0f))
        sixteenPoints.add(Vector3f(0.75f, 0.75f, 0f))
        sixteenPoints.add(Vector3f(0.45f, 0.9f, 0f))
        sixteenPoints.add(Vector3f(0f, 1f, 0f))
        sixteenPoints.add(Vector3f(-0.45f, 0.9f, 0f))
        sixteenPoints.add(Vector3f(-0.75f, 0.75f, 0f))
        sixteenPoints.add(Vector3f(-0.9f, 0.45f, 0f))
        sixteenPoints.add(Vector3f(-1f, 0f, 0f))
        sixteenPoints.add(Vector3f(-0.9f, -0.45f, 0f))
        sixteenPoints.add(Vector3f(-0.75f, -0.75f, 0f))
        sixteenPoints.add(Vector3f(-0.45f, -0.9f, 0f))
        sixteenPoints.add(Vector3f(0f, -1f, 0f))
        sixteenPoints.add(Vector3f(0.45f, -0.9f, 0f))
        sixteenPoints.add(Vector3f(0.75f, -0.75f, 0f))
        sixteenPoints.add(Vector3f(0.9f, -0.45f, 0f))

        //list of the vertices of the triangles from the cover of the curve
        val correctVerticesList = ArrayList<Vector3f>(14*3)

        //first iteration
        correctVerticesList.add(sixteenPoints[0])
        correctVerticesList.add(sixteenPoints[2])
        correctVerticesList.add(sixteenPoints[1])

        correctVerticesList.add(sixteenPoints[2])
        correctVerticesList.add(sixteenPoints[4])
        correctVerticesList.add(sixteenPoints[3])

        correctVerticesList.add(sixteenPoints[4])
        correctVerticesList.add(sixteenPoints[6])
        correctVerticesList.add(sixteenPoints[5])

        correctVerticesList.add(sixteenPoints[6])
        correctVerticesList.add(sixteenPoints[8])
        correctVerticesList.add(sixteenPoints[7])

        correctVerticesList.add(sixteenPoints[8])
        correctVerticesList.add(sixteenPoints[10])
        correctVerticesList.add(sixteenPoints[9])

        correctVerticesList.add(sixteenPoints[10])
        correctVerticesList.add(sixteenPoints[12])
        correctVerticesList.add(sixteenPoints[11])

        correctVerticesList.add(sixteenPoints[12])
        correctVerticesList.add(sixteenPoints[14])
        correctVerticesList.add(sixteenPoints[13])

        correctVerticesList.add(sixteenPoints[14])
        correctVerticesList.add(sixteenPoints[0])
        correctVerticesList.add(sixteenPoints[15])

        //second iteration
        correctVerticesList.add(sixteenPoints[0])
        correctVerticesList.add(sixteenPoints[4])
        correctVerticesList.add(sixteenPoints[2])

        correctVerticesList.add(sixteenPoints[4])
        correctVerticesList.add(sixteenPoints[8])
        correctVerticesList.add(sixteenPoints[6])

        correctVerticesList.add(sixteenPoints[8])
        correctVerticesList.add(sixteenPoints[12])
        correctVerticesList.add(sixteenPoints[10])

        correctVerticesList.add(sixteenPoints[12])
        correctVerticesList.add(sixteenPoints[0])
        correctVerticesList.add(sixteenPoints[14])

        //third and last iteration
        correctVerticesList.add(sixteenPoints[0])
        correctVerticesList.add(sixteenPoints[8])
        correctVerticesList.add(sixteenPoints[4])

        correctVerticesList.add(sixteenPoints[8])
        correctVerticesList.add(sixteenPoints[0])
        correctVerticesList.add(sixteenPoints[12])

        val calculatedVerticesList = Curve.VerticesCalculation.callPrivateFunc("getCoverVertices", sixteenPoints, true)

        assertEquals(calculatedVerticesList, correctVerticesList)
    }

    //Inline function to access private function in curve
    private inline fun <reified T> T.callPrivateFunc(name: String, vararg args: Any?): Any? =
            T::class
                    .declaredMemberFunctions
                    .firstOrNull { it.name == name }
                    ?.apply { isAccessible = true }
                    ?.call(this, *args)
}
