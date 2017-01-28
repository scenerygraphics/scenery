package graphics.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.*
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.OpenVRHMDInput
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.controls.behaviours.FPSCameraControl
import graphics.scenery.repl.REPL
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
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

            fun rangeRandomizer(min: Float, max: Float): Float = min + (Math.random().toFloat() * ((max - min) + 1.0f))

            cam.position = GLVector(0.0f, 0.0f, 0.0f)
            cam.perspectiveCamera(50.0f, 1280.0f, 720.0f)
            cam.active = true

            scene.addChild(cam)

            var boxes = (1..20).map {
                Box(GLVector(rangeRandomizer(0.5f, 4.0f),
                    rangeRandomizer(0.5f, 4.0f),
                    rangeRandomizer(0.5f, 4.0f)))
            }

            boxes.map { i ->
                i.position =
                    GLVector(rangeRandomizer(-10.0f, 10.0f),
                        rangeRandomizer(-10.0f, 10.0f),
                        rangeRandomizer(-10.0f, 10.0f))
                scene.addChild(i)
            }

            var lights = (0..127).map {
                PointLight()
            }

            lights.map {
                it.position = GLVector(rangeRandomizer(-600.0f, 600.0f),
                    rangeRandomizer(-600.0f, 600.0f),
                    rangeRandomizer(-600.0f, 600.0f))
                it.emissionColor = GLVector(rangeRandomizer(0.0f, 1.0f),
                    rangeRandomizer(0.0f, 1.0f),
                    rangeRandomizer(0.0f, 1.0f))
                it.intensity = rangeRandomizer(1.0f, 1000f)
                it.linear = 0.0f
                it.quadratic = 0.001f

                scene.addChild(it)
            }

            var companionBox = Box(GLVector(5.0f, 5.0f, 5.0f))
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

            val hullbox = Box(GLVector(100.0f, 100.0f, 100.0f))
            hullbox.position = GLVector(0.0f, 0.0f, 0.0f)
            val hullboxM = Material()
            hullboxM.ambient = GLVector(0.6f, 0.6f, 0.6f)
            hullboxM.diffuse = GLVector(0.4f, 0.4f, 0.4f)
            hullboxM.specular = GLVector(0.0f, 0.0f, 0.0f)
            hullboxM.doubleSided = true
            hullbox.material = hullboxM

            val mesh = Mesh()
            val meshM = Material()
            meshM.ambient = GLVector(0.5f, 0.5f, 0.5f)
            meshM.diffuse = GLVector(0.5f, 0.5f, 0.5f)
            meshM.specular = GLVector(0.0f, 0.0f, 0.0f)

            mesh.readFromOBJ(System.getenv("SCENERY_DEMO_FILES") + "/sponza.obj", useMTL = true)
            mesh.position = GLVector(0.0f, 5.0f, 0.0f)
            mesh.scale = GLVector(0.05f, 0.05f, 0.05f)
            mesh.updateWorld(true, true)
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
                            -128.0f + 18.0f * (i + 1),
                            5.0f + i * 5.0f,
                            (i + 1) * 50 * Math.cos(phi + (i * 0.2f)).toFloat())

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
