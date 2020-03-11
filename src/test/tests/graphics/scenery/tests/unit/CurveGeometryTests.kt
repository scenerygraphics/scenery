package graphics.scenery.tests.unit

import cleargl.GLVector
import graphics.scenery.numerics.Random
import graphics.scenery.CatmullRomSpline
import graphics.scenery.CurveGeometry
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

/**
 * This is the test class for the [CurveGeometry]
 *
 * @author Justin Bürger, burger@mail.mpi-cbg.com
 */
class CurveGeometryTests {
    private val logger by LazyLogger()

    @Test
    fun testCreation() {
        logger.info("This is the test for the CurveGeometry.")
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

        val geometry = CurveGeometry(curve)
        val frenetFrames = geometry.computeFrenetFrames(geometry.getCurve())

        fun triangleFalse(): ArrayList<GLVector> {
            val i = Random.randomFromRange(9.5f, 10.5f)
            val list = ArrayList<GLVector>()
            list.add(GLVector(0.3f, 0.3f, 0f))
            list.add(GLVector(0.3f, -0.3f, 0f))
            list.add(GLVector(-0.3f, -0.3f, 0f))
            return if(i >= 10 ) {
                list
            } else {
                list.add(GLVector(0f, 0f, 0f))
                list
            }
        }


        assertEquals(curve.catMullRomChain(), geometry.getCurve())
        assertNotNull(frenetFrames.forEach { it.normal })
        assertNotNull(frenetFrames.forEach{ it.bitangent })
        assertEquals(frenetFrames.filter { it.bitangent?.length2()!! < 1.1f && it.bitangent?.length2()!! > 0.9f },
                frenetFrames)
        assertEquals(frenetFrames.filter { it.normal?.length2()!! < 1.1f && it.normal?.length2()!! > 0.9f },
                frenetFrames)
        assertEquals(frenetFrames.filter { it.tangent.length2() < 1.1f && it.tangent.length2() > 0.9f },
                frenetFrames)
        assertFails {  geometry.drawSpline { triangleFalse() } }
    }

}
