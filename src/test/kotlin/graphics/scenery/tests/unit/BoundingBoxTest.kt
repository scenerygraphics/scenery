package graphics.scenery.tests.unit

import graphics.scenery.Box
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.Mesh
import graphics.scenery.PointLight
import graphics.scenery.Scene
import graphics.scenery.SceneryBase
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer.Companion.createRenderer
import graphics.scenery.numerics.Random
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Tests for bounding box calculation and intersection testing.
 *
 * @author Samuel Pantze
 *  */

class BoundingBoxTest : SceneryBase("BoundingBoxTest") {

    /** Test all scenarios */
    @Test
    fun testAllScenarios() {
        BoundingBoxScenarios.scenarios.forEachIndexed { index, scenario ->
            val testScene = Scene()
            val (baseBox, intersectBox) = BoundingBoxScenarios.createTestBoxes(testScene, scenario)

            val hit = intersectBox.spatial().intersects(baseBox, true)

            assertTrue(
                hit == scenario.shouldIntersect,
                "Scenario $index '${scenario.name}': Expected intersection=${scenario.shouldIntersect}, got=$hit"
            )
        }
    }
}

/** Visual demo. Run this to see scenarios */
class BoundingBoxVisualization(private val scenarioIndex: Int = 0) : SceneryBase("BoundingBoxVisualization") {

    /** Visualize the selected scenario */
    override fun init() {
        val scenario = BoundingBoxScenarios.scenarios[scenarioIndex]
        logger.info("Visualizing scenario: ${scenario.name}")

        renderer = hub.add(createRenderer(hub, applicationName, scene, 1280, 720))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0f, 0f, 15.0f)
            }
            perspectiveCamera(70.0f, windowWidth, windowHeight, 1.0f, 1000.0f)
            scene.addChild(this)
        }

        val (baseBox, intersectBox) = BoundingBoxScenarios.createTestBoxes(scene, scenario)

        val lights = (0..3).map {
            PointLight(radius = 100.0f)
        }.map {
            it.spatial {
                position = Random.Companion.random3DVectorFromRange(-50.0f, 50.0f)
            }
            it.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            it.intensity = Random.Companion.randomFromRange(0.5f, 1.5f)
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

        // Visualize result
        val hit = intersectBox.spatial().intersects(baseBox, true)
        logger.info("Intersection result: $hit (expected: ${scenario.shouldIntersect})")

        baseBox.material {
            diffuse = if (hit) Vector3f(1.0f, 0.3f, 0.2f) else Vector3f(0.8f, 0.9f, 0.87f)
        }
        intersectBox.material {
            diffuse = if (hit) Vector3f(0.9f, 0.2f, 0.08f) else Vector3f(0.7f, 0.8f, 0.8f)
        }
    }

    override fun inputSetup() {
        super.inputSetup()
        setupCameraModeSwitching()
    }

    companion object {
        private const val VISUALIZE_SCENARIO = 3

        @JvmStatic
        fun main(args: Array<String>) {
            BoundingBoxVisualization(VISUALIZE_SCENARIO).main()
        }
    }
}

/** Shared scenario definitions and factory */
object BoundingBoxScenarios {

    data class BoundingBoxScenario(
        val name: String,
        val baseBoxSize: Vector3f,
        val baseBoxTransform: (Mesh) -> Unit,
        val intersectBoxSize: Vector3f,
        val intersectBoxTransform: (Mesh) -> Unit,
        val shouldIntersect: Boolean
    )

    val scenarios = listOf(
        BoundingBoxScenario(
            name = "Overlapping boxes",
            baseBoxSize = Vector3f(4f, 1.5f, 0.5f),
            baseBoxTransform = { box ->
                box.spatial {
                    position = Vector3f(-7f, -2f, -2.0f)
                    scale = Vector3f(1.127E+1f, 4.187f, 2.991f)
                    rotation = Quaternionf(7.071E-1f, 4.699E-1f, 3.546E-1f, -3.917E-1f).normalize()
                }
            },
            intersectBoxSize = Vector3f(7f, 4f, 2f),
            intersectBoxTransform = { box ->
                box.spatial {
                    position = Vector3f(0f, 4f, 0f)
                    scale = Vector3f(5.654E-1f, 2.433f, 6.115f)
                    rotation = Quaternionf(2.069E-1f, -7.296E-1f, -4.159E-1f, -5.019E-1f).normalize()
                }
            },
            shouldIntersect = true
        ),
        BoundingBoxScenario(
            name = "Clearly separated aligned boxes",
            baseBoxSize = Vector3f(2f),
            baseBoxTransform = { box ->
                box.spatial {
                    position = Vector3f(0.0f, 0.0f, 0.0f)
                    scale = Vector3f(2f)
                    rotation = Quaternionf(0.0f, 0.0f, 0.0f, 1.0f)
                }
            },
            intersectBoxSize = Vector3f(2f),
            intersectBoxTransform = { box ->
                box.spatial {
                    position = Vector3f(4.0f, 0.0f, 0.0f)
                    scale = Vector3f(1f)
                    rotation = Quaternionf(0.0f, 0.0f, 0.0f, 1.0f)
                }
            },
            shouldIntersect = false
        ),
        BoundingBoxScenario(
            name = "Barely touching rotated boxes",
            baseBoxSize = Vector3f(2f),
            baseBoxTransform = { box ->
                box.spatial {
                    position = Vector3f(-0.5f, 0f, -1f)
                    scale = Vector3f(2f)
                    rotation = Quaternionf(0.3f, 0.2f, 0.1f, 1.0f).normalize()
                }
            },
            intersectBoxSize = Vector3f(2f),
            intersectBoxTransform = { box ->
                box.spatial {
                    position = Vector3f(4.0f, 4.0f, 0.0f)
                    scale = Vector3f(3f, 1f, 7f)
                    rotation = Quaternionf(0.6f, -.24f, -.5f, 1.0f).normalize()
                }
            },
            shouldIntersect = true
        ),
        BoundingBoxScenario(
            name = "Barely touching scaled boxes",
            baseBoxSize = Vector3f(1f, 2f, 8f),
            baseBoxTransform = { box ->
                box.spatial {
                    position = Vector3f(0f, 0f, -1f)
                    scale = Vector3f(2f)
                    rotation = Quaternionf(0f, -0.2f, -0.1f, 1.0f).normalize()
                }
            },
            intersectBoxSize = Vector3f(2f, 1f, 8f),
            intersectBoxTransform = { box ->
                box.spatial {
                    position = Vector3f(5.0f, 0.0f, -3.0f)
                    scale = Vector3f(1.6f)
                    rotation = Quaternionf(0.6f, -.24f, -.5f, 1.0f).normalize()
                }
            },
            shouldIntersect = true
        )
    )

    fun createTestBoxes(scene: Scene, scenario: BoundingBoxScenario): Pair<Mesh, Mesh> {
        val baseBox = Box(scenario.baseBoxSize)
        scenario.baseBoxTransform(baseBox)
        baseBox.material { diffuse = Vector3f(1f) }
        scene.addChild(baseBox)

        val intersectBox = Box(scenario.intersectBoxSize)
        scenario.intersectBoxTransform(intersectBox)
        intersectBox.material { diffuse = Vector3f(1f) }
        scene.addChild(intersectBox)

        baseBox.spatial().updateWorld(true, true)
        intersectBox.spatial().updateWorld(true, true)

        return Pair(baseBox, intersectBox)
    }
}
