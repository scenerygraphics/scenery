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

    private fun createTestBoxes(scene: Scene): Pair<Mesh, Mesh> {

        val baseBox = Box(Vector3f(4f, 1.5f, 0.5f))

        baseBox.spatial {
            position = Vector3f(-7f, -2f, -2.0f)
            scale = Vector3f(1.127E+1f,  4.187f, 2.991f)
            rotation = Quaternionf( 7.071E-1f, 4.699E-1f,  3.546E-1f, -3.917E-1f).normalize()
        }
        baseBox.material {
            diffuse = Vector3f(1f)
        }
        scene.addChild(baseBox)

        val intersectBox = Box(Vector3f(7f, 4f, 2f))
        intersectBox.spatial {
            // =====================================
            // CHANGE Y to 3f to let the test pass. In both cases the boxes clearly overlap visually.
            // =====================================
            position = Vector3f(0f, 4f, 0f)
            scale = Vector3f( 5.654E-1f, 2.433f, 6.115f)
            rotation = Quaternionf(2.069E-1f, -7.296E-1f, -4.159E-1f, -5.019E-1f).normalize()
        }
        intersectBox.material {
            diffuse = Vector3f(1f)
        }
        scene.addChild(intersectBox)

        // Since there are no rendered frames and thus no automatic world matrix updates, we do it here
        baseBox.spatial().updateWorld(true, true)
        intersectBox.spatial().updateWorld(true, true)

        return Pair(baseBox, intersectBox)
    }

    /** Test whether two boxes overlap. Each box is a child of a transformed parent. */
    @Test
    fun testOverlap() {
        val (baseBox, intersectBox) = createTestBoxes(scene)

        val hit = intersectBox.spatial().intersects(baseBox, true)

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

        val (baseBox, intersectBox) = createTestBoxes(scene)

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

        val hit = intersectBox.spatial().intersects(baseBox, true)
        logger.info("Intersection is $hit")
        baseBox.material {
            diffuse = if (hit) Vector3f(1.0f, 0.3f, 0.2f) else Vector3f(1f, 1f, 1f)
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
