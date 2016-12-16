package graphics.scenery.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.scenery.*
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import graphics.scenery.scenery.backends.Renderer
import graphics.scenery.scenery.controls.InputHandler
import graphics.scenery.scenery.controls.OpenVRInput
import graphics.scenery.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.scenery.controls.behaviours.FPSCameraControl
import graphics.scenery.scenery.repl.REPL
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
 */
class SponzaExample : SceneryDefaultApplication("SponzaExample", windowWidth = 1280, windowHeight = 720) {
    private var ovr: OpenVRInput? = null

    override fun init() {
        try {
            ovr = OpenVRInput(useCompositor = true)
            hub.add(SceneryElement.HMDINPUT, ovr!!)

            renderer = Renderer.createRenderer(applicationName,
                    scene,
                    1280,
                    720)
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

            var lights = (0..16).map {
                PointLight()
            }

            lights.map {
                it.position = GLVector(rangeRandomizer(-600.0f, 600.0f),
                        rangeRandomizer(-600.0f, 600.0f),
                        rangeRandomizer(-600.0f, 600.0f))
                it.emissionColor = GLVector(rangeRandomizer(0.0f, 1.0f),
                        rangeRandomizer(0.0f, 1.0f),
                        rangeRandomizer(0.0f, 1.0f))
                it.intensity = rangeRandomizer(0.01f, 1000f)
                it.linear = 0.1f;
                it.quadratic = 0.01f;

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

//                    scene.addChild(hullbox)

            val mesh = Mesh()
            val meshM = Material()
            meshM.ambient = GLVector(0.5f, 0.5f, 0.5f)
            meshM.diffuse = GLVector(0.5f, 0.5f, 0.5f)
            meshM.specular = GLVector(0.0f, 0.0f, 0.0f)

            mesh.readFromOBJ(System.getenv("SCENERY_DEMO_FILES") + "/sponza.obj", useMTL = true)
            //mesh.material = meshM
//            mesh.position = GLVector(155.5f, 150.5f, 55.0f)
            mesh.position = GLVector(0.0f, 5.0f, 0.0f)
            mesh.scale = GLVector(0.05f, 0.05f, 0.05f)
            mesh.rotation.rotateByEuler(0.05f, -0.2f, 1.11f)
            mesh.updateWorld(true, true)
            mesh.name = "Sponza_Mesh"

            scene.addChild(mesh)

            boxes.first().addChild(sphere)


            /*mesh.children.forEach {
                val bb = BoundingBox()
                bb.node = it
                bb.lineWidth = 0.7f
                it.boundingBoxCoords?.let { coords ->
                    bb.gridColor = GLVector(coords[0], coords[2], coords[4])*0.1f
                }
            }*/

            var ticks: Int = 0

            System.out.println(scene.children)

            thread {
                var reverse = false
                val step = 0.02f

                while (true) {
                    boxes.mapIndexed {
                        i, box ->
                        box.position.set(i % 3, step * ticks)
                        box.needsUpdate = true
                    }

                    lights.mapIndexed {
                        i, light ->
//                                light.position.set(i % 3, step*10 * ticks)
                        val phi = Math.PI * 2.0f * ticks / 500.0f

                        light.position = GLVector(
                                i * 10 * Math.sin(phi).toFloat() + Math.exp(i.toDouble()).toFloat(),
                                step * ticks,
                                i * 10 * Math.cos(phi).toFloat() + Math.exp(i.toDouble()).toFloat())

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

            repl = REPL(scene, renderer!!)
            repl?.start()
            repl?.showConsoleWindow()

        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    override fun inputSetup() {
        val target = GLVector(1.5f, 5.5f, 55.5f)
        val inputHandler = (hub.get(SceneryElement.INPUT) as InputHandler)
        val targetArcball = ArcballCameraControl("mouse_control", scene.findObserver(), renderer!!.window.width,  renderer!!.window.height, target)
        val fpsControl = FPSCameraControl("mouse_control", scene.findObserver(),  renderer!!.window.width, renderer!!.window.height)

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
