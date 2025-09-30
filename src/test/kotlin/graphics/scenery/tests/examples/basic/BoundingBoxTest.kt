package graphics.scenery.tests.examples.basic

import graphics.scenery.Box
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.Light
import graphics.scenery.Mesh
import graphics.scenery.PointLight
import graphics.scenery.Scene
import graphics.scenery.SceneryBase
import graphics.scenery.backends.Renderer
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertTrue


class BoundingBoxTest {

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

        val boxParent = Mesh()
        val box1 = Box(Vector3f(7f, 4f, 2f))
        boxParent.addChild(box1)
        boxParent.spatial {
            rotation = Quaternionf(-1.368e-1f, -4.826e-1f, -2.751e-1f, -8.201e-1f)
            scale = Vector3f(9.138e-1f, 1.463f, 7.328f)
        }
        box1.spatial {
            position = Vector3f(-2.500E+0f, -2.500E+0f, -2.500E+0f)
        }

        scene.addChild(boxParent)

        val intersectionParent = Mesh()
        intersectionParent.spatial {
            scale = Vector3f(2.566f, 1.005f, 1.354f)
            rotation = Quaternionf(5.284e-1f, 3.511e-1f, 2.650e-1f, -7.261e-1f)
            position = Vector3f(0.4f, 0f, 0f)
        }
        scene.addChild(intersectionParent)

        // Big rotating box for testing intersections
        val intersectionChild = Box(Vector3f(4f, 1.5f, 0.5f))
        intersectionChild.spatial {
            scale = Vector3f(3.0f, 3.0f, 3.0f)
            position = Vector3f(-7.0f, -2.0f, -2.0f)
        }
        intersectionParent.addChild(intersectionChild)

        intersectionParent.spatial().updateWorld(true, true)
        boxParent.spatial().updateWorld(true, true)

        val hit = intersectionChild.spatial().intersects(box1, true)
//
//        box1.ifMaterial {
//            diffuse = if (hit) Vector3f(1f, 0f, 0f) else Vector3f(1f, 1f, 1f)
//        }

        val lights = Light.createLightTetrahedron<PointLight>(spread = 10f, radius = 100f)

        lights.forEach { scene.addChild(it) }

        assertTrue(hit, "Boxes overlap.")
    }

}
