package graphics.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.controls.behaviours.FPSCameraControl
import graphics.scenery.utils.Numerics
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import java.io.IOException
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class OrientationBoxesExample: SceneryDefaultApplication("OrientationBoxesExample", windowWidth = 2560, windowHeight = 1600) {
    private var hmd: TrackedStereoGlasses? = null

    override fun init() {
        try {
//            hmd = OpenVRHMD(useCompositor = true)
            hmd = TrackedStereoGlasses("DTrack@10.1.2.201", "CAVEExample.yml")
            hub.add(SceneryElement.HMDInput, hmd!!)

            renderer = Renderer.createRenderer(hub, applicationName,
                scene,
                1280,
                800)
            hub.add(SceneryElement.Renderer, renderer!!)

            val cam: Camera = DetachedHeadCamera(hmd)
            cam.position = GLVector(0.0f, 0.0f, 0.0f)
            cam.perspectiveCamera(50.0f, 1280.0f, 720.0f, 0.5f, 100.0f)
//            cam.rotation.setFromEuler(Math.PI.toFloat()/2.0f, 0.0f, 0.0f)
            cam.active = true

            scene.addChild(cam)

            val lights = (0..16).map {
                Box(GLVector(0.5f, 0.5f, 0.5f))
            }

            val leftbox = Box(GLVector(1.0f, 1.0f, 1.0f))
            leftbox.position = GLVector(1.5f, 1.0f, -4.0f)
            leftbox.material.diffuse = GLVector(0.8f, 0.2f, 0.2f)
            leftbox.name = "leftbox"
            scene.addChild(leftbox)

            val bigbox = Box(GLVector(5.0f, 5.0f, 5.0f))
            bigbox.position = GLVector(-3.5f, 1.0f, -8.0f)
            bigbox.material.diffuse = GLVector(0.2f, 0.8f, 0.2f)
            bigbox.name = "leftbox"
            scene.addChild(bigbox)

            lights.map {
                it.position = Numerics.randomVectorFromRange(3, -600.0f, 600.0f)
                val mat = Material()
                mat.diffuse = Numerics.randomVectorFromRange(3, 0.0f, 1.0f)
                it.material = mat

                val light = PointLight()
                light.emissionColor = it.material.diffuse
                light.intensity = Numerics.randomFromRange(1.0f, 100f)
                light.linear = 1.8f
                light.quadratic = 0.7f

                it.addChild(light)

                scene.addChild(it)
            }

            val farbox = Box(GLVector(1.0f, 1.0f, 50.0f))
            farbox.material.diffuse = GLVector(0.2f, 0.2f, 0.8f)
            farbox.name = "farbox"
            farbox.position = GLVector(0.0f, 0.0f, -40.0f)
            scene.addChild(farbox)

            val floor = Box(GLVector(50.0f, 0.5f, 50.0f))
            floor.position = GLVector(0.0f, -0.5f, 0.0f)
            floor.material.diffuse = GLVector(0.5f, 0.5f, 0.5f)
            floor.name = "floor"
            scene.addChild(floor)

            var ticks: Int = 0

            thread {
                var reverse = false

                while (true) {
                    lights.mapIndexed {
                        i, light ->
                        val phi = Math.PI * 2.0f * ticks / 800.0f

                        light.position = GLVector(
                            -128.0f + 7.0f * (i + 1),
                            5.0f + i * 1.0f,
                            (i + 1) * 5.0f * Math.cos(phi + (i * 0.2f)).toFloat())

                        light.children[0].position = light.position

                    }

                    if (ticks >= 5000 && reverse == false) {
                        reverse = true
                    }
                    if (ticks <= 0 && reverse == true) {
                        reverse = false
                    }

                    if (reverse) {
                        ticks--
                    } else {
                        ticks++
                    }
                    Thread.sleep(50)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")
    }


    @Test override fun main() {
        super.main()
    }
}
