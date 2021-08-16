package graphics.scenery.tests.unit

import graphics.scenery.numerics.Random
import graphics.scenery.proteins.Axis
import graphics.scenery.utils.LazyLogger
import org.joml.Vector3f
import org.junit.Test
import kotlin.math.absoluteValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This is the test for the axis class.
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class AxisTests {
    private val logger by LazyLogger()

    /**
     * Tests what happens when the axis gets a list of less then four points as a parameter
     */
    @Test
    fun testTooFewPositions() {
        logger.info("Tests the result if to few positions are provided.")
        val list = listOf(Random.random3DVectorFromRange(-10f, 10f),Random.random3DVectorFromRange(-10f, 10f),
                Random.random3DVectorFromRange(-10f, 10f))
        val axis = Axis(list)
        assertEquals(axis.direction, Vector3f())
        assertEquals(axis.position, Vector3f())
    }

    /**
     * Test what happens when one of the positions for the axis becomes null.
     */
    @Test
    fun testNullPositions() {
        logger.info("Test the behaviour of the axis if one of the positions is null")
        val list = listOf(Random.random3DVectorFromRange(-10f, 10f),Random.random3DVectorFromRange(-10f, 10f),
                Random.random3DVectorFromRange(-10f, 10f), null)
        val axis = Axis(list)
        assertEquals(axis.direction, Vector3f())
        assertEquals(axis.position, Vector3f())
    }

    /**
     * Tests the least square approach for approximating the axis line.
     */
    @Test
    fun testLeastSquare() {
        logger.info("Test if the least square algorithm works good enough")
        //adding points around the z axis
        val points = ArrayList<Vector3f>(50)
        val p0= Vector3f(+0f, +1f, 0f)
        points.add(p0)
        val p1= Vector3f(-1f, -0f, 1f)
        points.add(p1)
        val p2= Vector3f(+0f, +1f, 2f)
        points.add(p2)
        val p3= Vector3f(-1f, -0f, 3f)
        points.add(p3)
        val p4= Vector3f(+0f, +1f, 4f)
        points.add(p4)
        val p5= Vector3f(-1f, -0f, 5f)
        points.add(p5)
        val p6= Vector3f(+0f, +1f, 6f)
        points.add(p6)
        val p7= Vector3f(-1f, -0f, 7f)
        points.add(p7)
        val p8= Vector3f(+0f, +1f, 8f)
        points.add(p8)
        val p9= Vector3f(-1f, -0f, 9f)
        points.add(p9)
        val p10= Vector3f(+0f, +1f, 10f)
        points.add(p10)
        val p11= Vector3f(-1f, -0f, 11f)
        points.add(p11)
        val p12= Vector3f(+0f, +1f, 12f)
        points.add(p12)
        val p13= Vector3f(-1f, -0f, 13f)
        points.add(p13)
        val p14= Vector3f(+0f, +1f, 14f)
        points.add(p14)
        val p15= Vector3f(-1f, -0f, 15f)
        points.add(p15)
        val p16= Vector3f(+0f, +1f, 16f)
        points.add(p16)
        val p17= Vector3f(-1f, -0f, 17f)
        points.add(p17)
        val p18= Vector3f(+0f, +1f, 18f)
        points.add(p18)
        val p19= Vector3f(-1f, -0f, 19f)
        points.add(p19)
        val p20= Vector3f(+0f, +1f, 20f)
        points.add(p20)
        val p21= Vector3f(-1f, -0f, 21f)
        points.add(p21)
        val p22= Vector3f(+0f, +1f, 22f)
        points.add(p22)
        val p23= Vector3f(-1f, -0f, 23f)
        points.add(p23)
        val p24= Vector3f(+0f, +1f, 24f)
        points.add(p24)
        val p25= Vector3f(-1f, -0f, 25f)
        points.add(p25)
        val p26= Vector3f(+0f, +1f, 26f)
        points.add(p26)
        val p27= Vector3f(-1f, -0f, 27f)
        points.add(p27)
        val p28= Vector3f(+0f, +1f, 28f)
        points.add(p28)
        val p29= Vector3f(-1f, -0f, 29f)
        points.add(p29)
        val p30= Vector3f(+0f, +1f, 30f)
        points.add(p30)
        val p31= Vector3f(-1f, -0f, 31f)
        points.add(p31)
        val p32= Vector3f(+0f, +1f, 32f)
        points.add(p32)
        val p33= Vector3f(-1f, -0f, 33f)
        points.add(p33)
        val p34= Vector3f(+0f, +1f, 34f)
        points.add(p34)
        val p35= Vector3f(-1f, -0f, 35f)
        points.add(p35)
        val p36= Vector3f(+0f, +1f, 36f)
        points.add(p36)
        val p37= Vector3f(-1f, -0f, 37f)
        points.add(p37)
        val p38= Vector3f(+0f, +1f, 38f)
        points.add(p38)
        val p39= Vector3f(-1f, -0f, 39f)
        points.add(p39)
        val p40= Vector3f(+0f, +1f, 40f)
        points.add(p40)
        val p41= Vector3f(-1f, -0f, 41f)
        points.add(p41)
        val p42= Vector3f(+0f, +1f, 42f)
        points.add(p42)
        val p43= Vector3f(-1f, -0f, 43f)
        points.add(p43)
        val p44= Vector3f(+0f, +1f, 44f)
        points.add(p44)
        val p45= Vector3f(-1f, -0f, 45f)
        points.add(p45)
        val p46= Vector3f(+0f, +1f, 46f)
        points.add(p46)
        val p47= Vector3f(-1f, -0f, 47f)
        points.add(p47)
        val p48= Vector3f(+0f, +1f, 48f)
        points.add(p48)
        val p49= Vector3f(-1f, -0f, 49f)
        points.add(p49)
        val p50= Vector3f(+0f, +1f, 50f)
        points.add(p50)

        val lq = Axis.leastSquare(points, points)
        //the direction should now be approximately the z axis
        assertTrue { lq.direction.x().absoluteValue < 0.1f}
        assertTrue { lq.direction.y().absoluteValue < 0.1f}
        assertTrue { 0.9f < lq.direction.z() && lq.direction.z() < 1.1f }
    }

}
