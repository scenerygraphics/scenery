package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.utils.Numerics
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.math.floor

/**
 * Demo loading the Sponza Model, demonstrating multiple moving lights
 * and transparent objects.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class SponzaExample : SceneryBase("SponzaExample", windowWidth = 1280, windowHeight = 720) {
    private var hmd: OpenVRHMD? = null//OpenVRHMD(false, true)

    override fun init() {
//        hub.add(SceneryElement.HMDInput, hmd!!)

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
            material.blending.transparent = true
            material.blending.opacity = 0.8f
            material.blending.colorBlending = Blending.BlendOp.add
            material.blending.alphaBlending = Blending.BlendOp.add
            material.diffuse = GLVector(0.9f, 0.1f, 0.1f)
            name = "transparent box"
            scene.addChild(this)
        }

        val lights = (0 until 128).map {
            Box(GLVector(0.1f, 0.1f, 0.1f))
        }.map {
            it.position = GLVector(
                Numerics.randomFromRange(-6.0f, 6.0f),
                Numerics.randomFromRange(0.1f, 1.2f),
                Numerics.randomFromRange(-10.0f, 10.0f)
            )

            it.material.diffuse = Numerics.randomVectorFromRange(3, 0.1f, 0.9f)

            val light = PointLight(radius = Numerics.randomFromRange(0.5f, 5.0f))
            light.emissionColor = it.material.diffuse
            light.intensity = Numerics.randomFromRange(1.0f, 2.0f)

            it.addChild(light)

            scene.addChild(it)
            it
        }

        val mesh = Mesh()
        with(mesh) {
            readFromOBJ(getDemoFilesPath() + "/sponza.obj", useMTL = true)
            rotation.rotateByAngleY(Math.PI.toFloat() / 2.0f)
            scale = GLVector(0.01f, 0.01f, 0.01f)
            name = "Sponza Mesh"

            scene.addChild(this)
        }

        thread {
            var ticks = 0L
            while (true) {
                lights.mapIndexed { i, light ->
                    val phi = (Math.PI * 2.0f * ticks / 1000.0f) % (Math.PI * 2.0f)

                    light.position = GLVector(
                        light.position.x(),
                        5.0f * Math.cos(phi + (i * 0.5f)).toFloat() + 5.2f,
                        light.position.z())

                    light.children.forEach { it.needsUpdateWorld = true }
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
