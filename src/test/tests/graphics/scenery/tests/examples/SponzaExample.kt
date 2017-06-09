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
class SponzaExample : SceneryDefaultApplication("SponzaExample", windowWidth = 2560, windowHeight = 1600) {
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
            cam.position = GLVector(0.0f, 1.0f, 0.0f)
            cam.perspectiveCamera(50.0f, 1280.0f, 720.0f)
//            cam.rotation.setFromEuler(Math.PI.toFloat()/2.0f, 0.0f, 0.0f)
            cam.active = true

            scene.addChild(cam)

            val lights = (0..16).map {
                Box(GLVector(0.5f, 0.5f, 0.5f))
            }

            val leftbox = Box(GLVector(2.0f, 2.0f, 2.0f))
            leftbox.position = GLVector(1.5f, 1.0f, -4.0f)
            leftbox.material.transparent = true
            leftbox.material.diffuse = GLVector(0.0f, 0.0f, 1.0f)
            leftbox.name = "leftbox"
            scene.addChild(leftbox)

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

            val mesh = Mesh()
            val meshM = Material()
            meshM.ambient = GLVector(0.5f, 0.5f, 0.5f)
            meshM.diffuse = GLVector(0.5f, 0.5f, 0.5f)
            meshM.specular = GLVector(0.0f, 0.0f, 0.0f)

            mesh.readFromOBJ(getDemoFilesPath() + "/sponza-crytek/sponza.obj", useMTL = false)
            mesh.position = GLVector(-200.0f, 5.0f, 200.0f)
            mesh.rotation.rotateByAngleY(Math.PI.toFloat()/2.0f)
            mesh.scale = GLVector(0.01f, 0.01f, 0.01f)
            mesh.name = "Sponza_Mesh"

            scene.addChild(mesh)

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
        val target = GLVector(1.5f, 5.5f, 55.5f)
        val inputHandler = (hub.get(SceneryElement.Input) as InputHandler)
        val targetArcball = ArcballCameraControl("mouse_control", scene.findObserver(), renderer!!.window.width, renderer!!.window.height, target)
        val fpsControl = FPSCameraControl("mouse_control", scene.findObserver(), renderer!!.window.width, renderer!!.window.height)

        val toggleControlMode = object : ClickBehaviour {
            var currentMode = "fps"

            override fun click(x: Int, y: Int) {
                if (currentMode.startsWith("fps")) {
                    targetArcball.target = scene.findObserver().position + scene.findObserver().forward

                    inputHandler.addBehaviour("mouse_control", targetArcball)
                    inputHandler.addBehaviour("scroll_arcball", targetArcball)
                    inputHandler.addKeyBinding("scroll_arcball", "scroll")

                    currentMode = "arcball"
                } else {
                    inputHandler.addBehaviour("mouse_control", fpsControl)
                    inputHandler.removeBehaviour("scroll_arcball")

                    currentMode = "fps"
                }

                System.out.println("Switched to $currentMode control")
            }
        }

        inputHandler.addBehaviour("toggle_control_mode", toggleControlMode)
        inputHandler.addKeyBinding("toggle_control_mode", "C")
    }


    @Test override fun main() {
        super.main()
    }
}
