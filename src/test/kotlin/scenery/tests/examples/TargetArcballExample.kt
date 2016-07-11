package scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.GLAutoDrawable
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import scenery.*
import scenery.controls.ClearGLInputHandler
import scenery.controls.behaviours.FPSCameraControl
import scenery.controls.behaviours.TargetArcBallCameraControl
import scenery.rendermodules.opengl.DeferredLightingRenderer
import scenery.repl.REPL
import kotlin.concurrent.thread

/**
 * This example demonstrates how to use the TargetArcBallBehaviour and how
 * to modify the default behaviour/key map of scenery.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TargetArcballExample : SceneryDefaultApplication("TargetArcballExample") {
    override fun init(pDrawable: GLAutoDrawable) {
        deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4, glWindow!!.width, glWindow!!.height)
        hub.add(SceneryElement.RENDERER, deferredRenderer!!)

        val boxmaterial = Material()
        with(boxmaterial) {
            ambient = GLVector(1.0f, 0.0f, 0.0f)
            diffuse = GLVector(0.0f, 1.0f, 0.0f)
            specular = GLVector(1.0f, 1.0f, 1.0f)
            textures.put("diffuse", TexturedCubeExample::class.java.getResource("textures/helix.png").file)
        }

        val box = Box(GLVector(1.0f, 1.0f, 1.0f))

        with(box) {
            box.material = boxmaterial
            box.position = GLVector(0.0f, 0.0f, 0.0f)
            scene.addChild(this)
        }

        val lights = (0..2).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
            light.intensity = 0.2f * (i + 1)
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, -5.0f)
            view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)
            projection = GLMatrix()
                .setPerspectiveProjectionMatrix(
                    70.0f / 180.0f * Math.PI.toFloat(),
                    1024f / 1024f, 0.1f, 1000.0f)
            active = true

            scene.addChild(this)
        }

        thread {
            while (true) {
                box.rotation.rotateByAngleY(0.01f)
                box.needsUpdate = true

                Thread.sleep(20)
            }
        }

        deferredRenderer?.initializeScene(scene)

        repl = REPL(scene, deferredRenderer!!)
        repl?.start()
        repl?.showConsoleWindow()
    }

    override fun inputSetup() {
        val target = GLVector(0.5f, 0.5f, 0.5f)
        val inputHandler = (hub.get(SceneryElement.INPUT) as ClearGLInputHandler)
        val targetArcball = TargetArcBallCameraControl("mouse_control", scene.findObserver(), glWindow!!.width, glWindow!!.height, target)
        val fpsControl = FPSCameraControl("mouse_control", scene.findObserver(), glWindow!!.width, glWindow!!.height)

        val toggleControlMode = object : ClickBehaviour {
            var currentMode = "fps"

            override fun click(x: Int, y: Int) {
                if (currentMode.startsWith("fps")) {
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
