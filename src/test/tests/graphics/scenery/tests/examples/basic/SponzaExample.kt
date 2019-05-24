package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread

/**
 * Demo loading the Sponza Model, demonstrating multiple moving lights
 * and transparent objects.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class SponzaExample : SceneryBase("SponzaExample", windowWidth = 1280, windowHeight = 720) {
    private var movingLights = true

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName,
            scene,
            windowWidth,
            windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            cam.position = GLVector(0.0f, 1.0f, 0.0f)
            cam.perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            cam.active = true
            scene.addChild(this)
        }

        val lights = (0 until 128).map {
            Box(GLVector(0.1f, 0.1f, 0.1f))
        }.map {
            it.position = GLVector(
                Random.randomFromRange(-6.0f, 6.0f),
                Random.randomFromRange(0.1f, 1.2f),
                Random.randomFromRange(-10.0f, 10.0f)
            )

            it.material.diffuse = Random.randomVectorFromRange(3, 0.1f, 0.9f)

            val light = PointLight(radius = Random.randomFromRange(0.5f, 5.0f))
            light.emissionColor = it.material.diffuse
            light.intensity = Random.randomFromRange(0.1f, 0.5f)

            it.addChild(light)

            scene.addChild(it)
            it
        }

        val mesh = Mesh()
        with(mesh) {
            readFromOBJ(getDemoFilesPath() + "/sponza.obj", importMaterials = true)
            rotation.rotateByAngleY(Math.PI.toFloat() / 2.0f)
            scale = GLVector(0.01f, 0.01f, 0.01f)
            name = "Sponza Mesh"

            scene.addChild(this)
        }

        val desc = TextBoard()
        desc.text = "sponza"
        desc.position = GLVector(-2.0f, -0.1f, -4.0f)
        desc.fontColor = GLVector(0.0f, 0.0f, 0.0f)
        desc.backgroundColor = GLVector(0.1f, 0.1f, 0.1f)
        desc.transparent = 0
        scene.addChild(desc)

        thread {
            var ticks = 0L
            while (true) {
                if(movingLights) {
                    lights.mapIndexed { i, light ->
                        val phi = (Math.PI * 2.0f * ticks / 1000.0f) % (Math.PI * 2.0f)

                        light.position = GLVector(
                            light.position.x(),
                            5.0f * Math.cos(phi + (i * 0.5f)).toFloat() + 5.2f,
                            light.position.z())

                        light.children.forEach { it.needsUpdateWorld = true }
                    }

                    ticks++
                }

                Thread.sleep(15)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")

        inputHandler?.addBehaviour("toggle_light_movement", ClickBehaviour { _, _ -> movingLights = !movingLights })
        inputHandler?.addKeyBinding("toggle_light_movement", "T")
    }

    @Test override fun main() {
        super.main()
    }
}
