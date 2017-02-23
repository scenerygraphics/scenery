package graphics.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.OpenVRHMDInput
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
class SponzaExample : SceneryDefaultApplication("SponzaExample", windowWidth = 1280, windowHeight = 720) {
    private var ovr: OpenVRHMDInput? = null

    override fun init() {
        try {
            ovr = OpenVRHMDInput(useCompositor = true)
            hub.add(SceneryElement.HMDINPUT, ovr!!)

            renderer = Renderer.createRenderer(hub, applicationName,
                scene,
                1280,
                800)
            hub.add(SceneryElement.RENDERER, renderer!!)

            val cam: Camera = DetachedHeadCamera()
            cam.position = GLVector(0.0f, 5.0f, 0.0f)
            cam.perspectiveCamera(50.0f, 1280.0f, 720.0f)
            cam.rotation.setFromEuler(Math.PI.toFloat()/2.0f, 0.0f, 0.0f)
            cam.active = true

            scene.addChild(cam)

            val boxes = (1..20).map { Box(Numerics.randomVectorFromRange(3, 0.5f, 4.0f)) }

            boxes.map { i ->
                i.position = Numerics.randomVectorFromRange(3, -10.0f, 10.0f)
                scene.addChild(i)
            }

            val lights = (0..16).map {
                PointLight()
            }

            lights.map {
                it.position = Numerics.randomVectorFromRange(3, -600.0f, 600.0f)
                it.emissionColor = Numerics.randomVectorFromRange(3, 0.0f, 1.0f)
                it.intensity = Numerics.randomFromRange(1.0f, 100f)
                it.linear = 0.0f
                it.quadratic = 0.001f

                scene.addChild(it)
            }

            val companionBox = Box(GLVector(5.0f, 5.0f, 5.0f))
            companionBox.position = GLVector(1.0f, 1.0f, 1.0f)
            companionBox.name = "Le Box de la Compagnion"
            val companionBoxMaterial = Material()
            companionBoxMaterial.ambient = GLVector(1.0f, 0.5f, 0.0f)
            companionBoxMaterial.diffuse = GLVector(1.0f, 0.0f, 0.0f)
            companionBoxMaterial.specular = GLVector(1.0f, 0.0f, 0.0f)

            companionBox.material = companionBoxMaterial

            boxes.first().addChild(companionBox)

            val sphere = Sphere(0.5f, 20)
            sphere.position = GLVector(0.5f, -1.2f, 0.5f)
            sphere.scale = GLVector(5.0f, 5.0f, 5.0f)

            val mesh = Mesh()
            val meshM = Material()
            meshM.ambient = GLVector(0.5f, 0.5f, 0.5f)
            meshM.diffuse = GLVector(0.5f, 0.5f, 0.5f)
            meshM.specular = GLVector(0.0f, 0.0f, 0.0f)

            mesh.readFromOBJ(getDemoFilesPath() + "/sponza.obj", useMTL = true)
            mesh.position = GLVector(0.0f, 5.0f, 0.0f)
            mesh.scale = GLVector(0.05f, 0.05f, 0.05f)
            mesh.name = "Sponza_Mesh"

            scene.addChild(mesh)

            boxes.first().addChild(sphere)

            var ticks: Int = 0

            System.out.println(scene.children)

            thread {
                var reverse = false
                val step = 0.1f

                while (true) {
                    boxes.mapIndexed {
                        i, box ->
                        box.position.set(i % 3, step * ticks)
                        box.needsUpdate = true
                    }

                    lights.mapIndexed {
                        i, light ->
                        val phi = Math.PI * 2.0f * ticks / 800.0f

                        light.position = GLVector(
                            -128.0f + 72.0f * (i + 1),
                            5.0f + i * 5.0f,
                            (i + 1) * 150 * Math.cos(phi + (i * 0.2f)).toFloat())

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

                    boxes.first().rotation.rotateByEuler(0.01f, 0.0f, 0.0f)
                    boxes.first().needsUpdate = true
                    companionBox.needsUpdate = true
                    sphere.needsUpdate = true
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    override fun inputSetup() {
        val target = GLVector(1.5f, 5.5f, 55.5f)
        val inputHandler = (hub.get(SceneryElement.INPUT) as InputHandler)
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
