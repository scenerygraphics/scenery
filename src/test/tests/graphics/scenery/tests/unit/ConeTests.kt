package graphics.scenery.tests.unit

import cleargl.GLVector
import graphics.scenery.Cone
import graphics.scenery.Scene
import graphics.scenery.numerics.Random
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the [Cone] primitive.
 *
 * @author Aryaman Gupta <aryaman1994@gmail.com>
 */
class ConeTests {
    private val logger by LazyLogger()

    @Test
    fun testCreation() {
        logger.info("Testing Cone creating and bounding box calculation ...")
        val s = Scene()
        val radius = Random.randomFromRange(0.1f, 5000.0f)
        val height = Random.randomFromRange(0.1f, 5000.0f)
        val axis = Random.randomVectorFromRange(3, -1.0f, 1.0f)
        val segments = kotlin.random.Random.nextInt(1, 10)

        val epsilon = 0.0001f
        val axisN = axis.normalize()

        val c = Cone(radius, height, 4, axisN)
        s.addChild(c)

        val bb = c.boundingBox
        logger.info("min x: ${bb!!.min.x()}")
        logger.info("min y: ${bb!!.min.y()}")
        logger.info("min z: ${bb!!.min.z()}")

        logger.info("max x: ${bb!!.max.x()}")
        logger.info("max y: ${bb!!.max.y()}")
        logger.info("max z: ${bb!!.max.z()}")

        logger.info("height is $height")
        logger.info("radius is $radius")


        val directedHeight = axisN * height

        val d = -1 * ((bb.min.x() * axisN.x()) + (bb.min.y() * axisN.y()) + (bb.min.z() * axisN.z()))

        val expHeight = ((bb.max.x() * axisN.x()) + (bb.max.y() * axisN.y()) + (bb.max.z() * axisN.z())) + d

        logger.info("Expected height is: $expHeight")

        assertNotNull(bb)
//        assertTrue(bb.min.x() >= -size.x() / 2.0f - epsilon)
//        assertTrue(bb.min.y() >= -size.y() / 2.0f - epsilon)
//        assertTrue(bb.min.z() >= -size.z() / 2.0f - epsilon)
//
//        assertTrue(bb.max.x() <= size.x() / 2.0f + epsilon)
//        assertTrue(bb.max.y() <= size.y() / 2.0f + epsilon)
//        assertTrue(bb.max.z() <= size.z() / 2.0f + epsilon)
    }
}
