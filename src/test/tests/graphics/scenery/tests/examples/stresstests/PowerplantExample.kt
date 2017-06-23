package graphics.scenery.tests.examples.stresstests

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.Numerics
import org.junit.Test
import kotlin.concurrent.thread

/**
 * Stress-testing demo with the PowerPlant model from UNC, and lots of lights.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class PowerplantExample : SceneryDefaultApplication("PowerplantExample", windowWidth = 1280, windowHeight = 720) {
    override fun init() {
        val lightCount = 127

        logger.warn("ADDITIONAL FILES NEEDED")
        logger.warn("This example needs an additional model file, which is not available as part of the")
        logger.warn("example models zip. Please download it from: http://graphics.cs.williams.edu/data/meshes.xml#12")

        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 0.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat(), nearPlaneLocation = 0.5f, farPlaneLocation = 1000.0f)
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
            it.emissionColor = Numerics.randomVectorFromRange(3, 0.0f, 1.0f)
            it.parent?.material?.diffuse = it.emissionColor
            it.intensity = Numerics.randomFromRange(0.01f, 10f)
            it.linear = 0.01f
            it.quadratic = 0.01f

            scene.addChild(it)
        }

        val plant = Mesh()
        with(plant) {
            readFromOBJ(getDemoFilesPath() + "/powerplant.obj", useMTL = true)
            position = GLVector(0.0f, 0.0f, 0.0f)
            scale = GLVector(0.001f, 0.001f, 0.001f)
            material = Material()
            updateWorld(true, true)
            name = "rungholt"

            scene.addChild(this)
        }

        thread {
            var ticks = 0L
            while (true) {
                boxes.mapIndexed {
                    i, box ->
                    val phi = Math.PI * 2.0f * ticks / 2500.0f

                    box.position = GLVector(
                        -128.0f + 18.0f * (i + 1),
                        5.0f + i * 5.0f,
                        (i + 1) * 50 * Math.cos(phi + (i * 0.2f)).toFloat())

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

