package graphics.scenery.tests.examples.basic

import graphics.scenery.Box
import graphics.scenery.Camera
import graphics.scenery.Light
import graphics.scenery.Mesh
import graphics.scenery.PointLight
import graphics.scenery.Scene
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for bounding box calculation and intersection testing.
 *
 * @author Samuel Pantze
 *  */

class BoundingBoxTest {

    /** Test whether two boxes overlap. Each box is a child of a transformed parent. */
    @Test
    fun testOverlap() {

        val scene = Scene()
        val cam = Camera()
        scene.addChild(cam)

        with(cam) {
            spatial {
                position = Vector3f(0f, 0f, 6.0f)
            }
            perspectiveCamera(70.0f, 1000, 1000, 1.0f, 1000.0f)
            scene.addChild(this)
        }

        // Box parent and box child are the objects to be tested against
        val boxParent = Mesh()
        val boxChild = Box(Vector3f(7f, 4f, 2f))
        boxParent.addChild(boxChild)
        boxParent.spatial {
            rotation = Quaternionf(-1.368e-1f, -4.826e-1f, -2.751e-1f, -8.201e-1f)
            scale = Vector3f(9.138e-1f, 1.463f, 7.328f)
        }
        boxChild.spatial {
            position = Vector3f(-2.500E+0f, -2.500E+0f, -2.500E+0f)
        }
        scene.addChild(boxParent)

        // Intersection parent and child are the intersection tester objects
        val intersectionParent = Mesh()
        intersectionParent.spatial {
            scale = Vector3f(2.566f, 1.005f, 1.354f)
            rotation = Quaternionf(5.284e-1f, 3.511e-1f, 2.650e-1f, -7.261e-1f)
            position = Vector3f(0.4f, 0f, 0f)
        }
        scene.addChild(intersectionParent)

        val intersectionChild = Box(Vector3f(4f, 1.5f, 0.5f))
        intersectionChild.spatial {
            scale = Vector3f(3.0f, 3.0f, 3.0f)
            position = Vector3f(-7.0f, -2.0f, -2.0f)
        }
        intersectionParent.addChild(intersectionChild)

        // Since there are no renderes frames and thus no automatic world matrix updates, we do it here
        intersectionParent.spatial().updateWorld(true, true)
        boxParent.spatial().updateWorld(true, true)

        val hit = intersectionChild.spatial().intersects(boxChild, true)

        assertTrue(hit, "Boxes overlap.")
    }

}
