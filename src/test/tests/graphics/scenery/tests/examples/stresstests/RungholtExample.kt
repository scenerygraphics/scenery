package graphics.scenery.tests.examples.stresstests

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.numerics.Random
import org.junit.Test
import kotlin.concurrent.thread

/**
 * Stress-testing demo with the Rungholt model, and even greater number of lights.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class RungholtExample : SceneryBase("RungholtExample", windowWidth = 1280, windowHeight = 720) {
    var hmd: OpenVRHMD? = null
    override fun init() {
        val lightCount = 512

        logger.warn("ADDITIONAL FILES NEEDED")
        logger.warn("This example needs an additional model file, which is not available as part of the")
        logger.warn("example models zip. Please download it from: http://graphics.cs.williams.edu/data/meshes.xml#13")

        hmd = OpenVRHMD(useCompositor = true)
        hub.add(SceneryElement.HMDInput, hmd!!)

        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat(), nearPlaneLocation = 0.5f, farPlaneLocation = 1000.0f)
            position = GLVector(0.0f, 50.0f, -100.0f)
            active = true
            scene.addChild(this)
        }

        val boxes = (0..lightCount).map {
            Box(GLVector(0.5f, 0.5f, 0.5f))
        }

        val lights = (0..lightCount).map {
            PointLight()
        }

        boxes.mapIndexed { i, box ->
            box.material = Material()
            box.addChild(lights[i])
            scene.addChild(box)
        }

        lights.map {
            it.emissionColor = Random.randomVectorFromRange(3, 0.0f, 1.0f)
            it.parent?.material?.diffuse = it.emissionColor
            it.intensity = Random.randomFromRange(0.1f, 10f)
            it.linear = 1.2f
            it.quadratic = 0.2f

            scene.addChild(it)
        }

        val rungholtMesh = Mesh()
        with(rungholtMesh) {
            readFromOBJ(getDemoFilesPath() + "/rungholt.obj", importMaterials = true)
            position = GLVector(0.0f, 0.0f, 0.0f)
            scale = GLVector(1.0f, 1.0f, 1.0f)
            updateWorld(true, true)
            name = "rungholt"

            scene.addChild(this)
        }

        thread {
            var ticks = 0L
            while (true) {
                boxes.mapIndexed {
                    i, box ->
                    val phi = ticks / 1500.0f % (Math.PI * 2.0f)

                    box.position = GLVector(
                        -320.0f + 5.0f * (i + 1),
                        15.0f + i * 0.2f,
                        250.0f * Math.cos(phi + (i * 0.2f)).toFloat())
                    box.children[0].position = box.position

                }

                ticks++
                Thread.sleep(10)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}

