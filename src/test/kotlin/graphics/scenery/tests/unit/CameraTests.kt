package graphics.scenery.tests.unit

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.TextBoard
import graphics.scenery.utils.lazyLogger
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the [Camera] class.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class CameraTests {
    private val logger by lazyLogger()

    /**
     * Tests [Camera.showMessage] by showing a message, expecting
     * that to become part of the scene graph, and being removed
     * from it again after a given duration.
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun testShowMessage() {
        val duration = 400
        val s = Scene()
        val cam = Camera()
        s.addChild(cam)

        cam.showMessage("hello camera", duration = duration)
        assertTrue("Scene contains messages TextBoard from Camera") { (cam.children.last() as? TextBoard)?.text == "hello camera" }
        assertTrue("Camera contains messages metadata object") { cam.metadata.containsKey("messages") }
        Thread.sleep(2L * duration)

        assertTrue("TextBoard was removed from scene after showing duration") { cam.children.size == 0 }
        assertTrue("Messages metadata object contains no more entries") { (cam.metadata["messages"] as MutableList<Node>).isEmpty() }
    }

    /**
     * Tests [Camera.canSee] by adding nodes in front and behind the
     * camera, and checking whether they are visible from the camera's viewpoint.
     */
    @Test
    fun testCanSee() {
        val s = Scene()
        val cam = Camera()
        cam.perspectiveCamera(50.0f, 1280, 720, 0.01f, 1000.0f)
        s.addChild(cam)

        val boxesInFront = (0 until 10).map {
            val b = Box()
            b.spatial().position = Vector3f(0.0f,
                Random.randomFromRange(-0.5f, 0.5f),
                Random.randomFromRange(2.0f, 10.0f))
            s.addChild(b)
            b
        }

        val boxesBehind = (0 until 10).map {
            val b = Box()
            b.spatial().position = Random.random3DVectorFromRange(-0.5f, -10.0f)
            s.addChild(b)
            b
        }

        s.spatial().updateWorld(true, true)

        assertTrue { boxesInFront.all { cam.canSee(it) } }
        assertFalse { boxesBehind.all { cam.canSee(it) } }
    }

    /**
     * Tests [Camera.getNodesForScreenSpacePosition] by adding nodes in front of
     * the camera and checking the number of nodes at a given
     * screen position. The camera shoots a ray through 0|0 and should return the
     * same number of nodes that where added to the scene (10), as it should hit all of them.
     */
    @Test
    fun testPickFromScreenPos() {
        val s = Scene()
        val cam = Camera()
        cam.perspectiveCamera(50.0f, 1280, 720, 0.01f, 1000.0f)
        s.addChild(cam)

        (0 until 10).map {
            val b = Box()
            b.spatial().position = Vector3f(0.0f,
                0f,
                Random.randomFromRange(-2.0f, -10.0f))
            s.addChild(b)
            b
        }

        s.spatial().updateWorld(true, true)
        val results = cam.getNodesForScreenSpacePosition(1280/2,720/2)
        assertEquals(10,results.matches.size)
    }

    /**
     * Tests [Camera.screenToPointRay] From the camera, rays should be generated in front of it,
     * starting at the near clipping plane and going through an imaginary plane 1 Unit away from the camera.
     * The planes size depends on the FOV and aspect ratio of the camera.
     * The expected ray start in world space should be at 0|0|-nearPlaneDist with direction 0|0|-1.0
     */
    @Test
    fun testCastRayFromScreenPos() {
        val s = Scene()
        val cam = Camera()
        val nearPlaneLocation = 0.01f
        cam.perspectiveCamera(50.0f, 1280, 720, nearPlaneLocation, 1000.0f)
        s.addChild(cam)

        cam.spatial {
            position = Vector3f(0f)
            rotation.lookAlong(Vector3f(0f,0f,1f),Vector3f(0f,1f,0f))
        }

        val (pos,dir) = cam.screenPointToRay(1280/2,720/2)

        val epsilon = 0.001f
        assertEquals(0.0f, pos.x, epsilon, "Position X")
        assertEquals(0.0f, pos.y, epsilon, "Position Y")
        assertEquals(-nearPlaneLocation, pos.z, epsilon, "Position Z")

        assertEquals(0.0f, dir.x, epsilon, "Direction X")
        assertEquals(0.0f, dir.y, epsilon, "Direction Y")
        assertEquals(-1.0f, dir.z, epsilon, "Direction Z")

    }
}
