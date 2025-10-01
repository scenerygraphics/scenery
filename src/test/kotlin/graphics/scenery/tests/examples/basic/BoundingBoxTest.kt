package graphics.scenery.tests.examples.basic

import graphics.scenery.Box
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.Mesh
import graphics.scenery.PointLight
import graphics.scenery.Scene
import graphics.scenery.SceneryBase
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material
import graphics.scenery.numerics.Random
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for bounding box calculation and intersection testing.
 *
 * @author Samuel Pantze
 *  */

class BoundingBoxTest : SceneryBase("BoundingBoxTest") {

    data class Quad<out A, out B, out C, out D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    ) {}

    private fun createTestBoxes(scene: Scene): Quad<Mesh, Box, Mesh, Box> {
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
        boxChild.material {
            diffuse = Vector3f(1.0f, 1.0f, 1.0f)
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
        intersectionChild.material {
            diffuse = Vector3f(0.2f, 1.0f, 0.3f)
        }
        intersectionParent.addChild(intersectionChild)

        return Quad(boxParent, boxChild, intersectionParent, intersectionChild)
    }

    /** Test whether two boxes overlap. Each box is a child of a transformed parent. */
    @Test
    fun testOverlap() {
        val scene = Scene()
        val (boxChild, boxParent, intersectionChild, intersectionParent) = createTestBoxes(scene)

        // Since there are no rendered frames and thus no automatic world matrix updates, we do it here
        boxParent.spatial().updateWorld(true, true)
        intersectionParent.spatial().updateWorld(true, true)

        println()
        println("What are the bounding boxes?")
        println("boxParent: ${boxParent.boundingBox}")
        println("intersectionParent: ${intersectionParent.boundingBox}")
        println("boxChild: ${boxChild.boundingBox}")
        println("intersectionChild: ${intersectionChild.boundingBox}")
        println("Approx overlap? ${intersectionChild.spatial().intersects(boxChild, false)}")
        println("Precise overlap? ${intersectionChild.spatial().intersects(boxChild, true)}")


        println()
        println("Generating bounding boxes for parents including children!")
        boxParent.generateBoundingBox(true)
        intersectionParent.generateBoundingBox(true)

        println()
        println("How about now?")
        println("boxParent: ${boxParent.boundingBox}")
        println("intersectionParent: ${intersectionParent.boundingBox}")
        println("boxChild: ${boxChild.boundingBox}")
        println("intersectionChild: ${intersectionChild.boundingBox}")
        println("Approx overlap? ${intersectionChild.spatial().intersects(boxChild, false)}")
        println("Precise overlap? ${intersectionChild.spatial().intersects(boxChild, true)}")

        println()
        println("Generating bounding boxes for children directly!")
        boxChild.generateBoundingBox(true)
        intersectionChild.generateBoundingBox(true)

        println()
        println("And now how is it?")
        println("boxParent: ${boxParent.boundingBox}")
        println("intersectionParent: ${intersectionParent.boundingBox}")
        println("boxChild: ${boxChild.boundingBox}")
        println("intersectionChild: ${intersectionChild.boundingBox}")
        println("Approx overlap? ${intersectionChild.spatial().intersects(boxChild, false)}")
        println("Precise overlap? ${intersectionChild.spatial().intersects(boxChild, true)}")

        val hit = intersectionChild.spatial().intersects(boxChild, true)

        assertTrue(hit, "Overlapping boxes expected.")
    }

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 1280, 720))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0f, 0f, 15.0f)
            }
            perspectiveCamera(70.0f, windowWidth, windowHeight, 1.0f, 1000.0f)
            scene.addChild(this)
        }

        createTestBoxes(scene)

        val lights = (0..3).map {
            PointLight(radius = 100.0f)
        }.map {
            it.spatial {
                position = Random.random3DVectorFromRange(-50.0f, 50.0f)
            }
            it.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            it.intensity = Random.randomFromRange(0.5f, 1.5f)
            it
        }

        lights.forEach { scene.addChild(it) }

        val hullbox = Box(Vector3f(100.0f, 100.0f, 100.0f), insideNormals = true)
        with(hullbox) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 0.0f)
            }

            material {
                ambient = Vector3f(0.6f, 0.6f, 0.6f)
                diffuse = Vector3f(0.4f, 0.4f, 0.4f)
                specular = Vector3f(0.0f, 0.0f, 0.0f)
                cullingMode = Material.CullingMode.Front
            }

            scene.addChild(this)
        }
    }

    override fun inputSetup() {
        super.inputSetup()
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            BoundingBoxTest().main()
        }
    }

}
