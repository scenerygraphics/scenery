package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.utils.Numerics
import org.junit.Test
import kotlin.concurrent.thread

/**
 * Demo loading the Sponza Model, demonstrating multiple moving lights
 * and transparent objects.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class SponzaExample : SceneryBase("SponzaExample", windowWidth = 1280, windowHeight = 720) {
    private var hmd: TrackedStereoGlasses? = null

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName,
            scene,
            windowWidth,
            windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            cam.position = GLVector(0.0f, 1.0f, 0.0f)
            cam.perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            cam.active = true
            scene.addChild(this)
        }

        val transparentBox = Box(GLVector(2.0f, 2.0f, 2.0f))
        with(transparentBox) {
            position = GLVector(1.5f, 1.0f, -4.0f)
            material.transparent = true
            material.diffuse = GLVector(0.0f, 0.0f, 1.0f)
            name = "transparent box"
            scene.addChild(this)
        }

        val lights = (0..16).map {
            Box(GLVector(0.1f, 0.1f, 0.1f))
        }.map {
            it.position = Numerics.randomVectorFromRange(3, -600.0f, 600.0f)
            it.material.diffuse = Numerics.randomVectorFromRange(3, 0.0f, 1.0f)

            val light = PointLight()
            light.emissionColor = it.material.diffuse
            light.intensity = Numerics.randomFromRange(1.0f, 50f)
            light.linear = 0.0f
            light.quadratic = 2.2f

            it.addChild(light)

            scene.addChild(it)
            it
        }

        val mesh = Mesh()
        with(mesh) {
            readFromOBJ(getDemoFilesPath() + "/sponza.obj", useMTL = true)
            position = GLVector(-200.0f, 5.0f, 200.0f)
            rotation.rotateByAngleY(Math.PI.toFloat() / 2.0f)
            scale = GLVector(0.01f, 0.01f, 0.01f)
            name = "Sponza Mesh"

            scene.addChild(this)
        }

        thread {
            var ticks = 0L
            while (true) {
                lights.mapIndexed { i, light ->
                    val phi = (Math.PI * 2.0f * ticks / 500.0f) % (Math.PI * 2.0f)

                    light.position = GLVector(
                        5.0f * Math.cos(phi + (i * 0.5f)).toFloat(),
                        0.1f + i * 0.2f,
                        -20.0f + 2.0f * i)

                    light.children[0].position = light.position
                }

                ticks++
                Thread.sleep(15)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")
    }

    @Test override fun main() {
        super.main()
    }
}
