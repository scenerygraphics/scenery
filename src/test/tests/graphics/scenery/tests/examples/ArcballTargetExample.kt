package graphics.scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.behaviours.ArcballCameraControl
import graphics.scenery.controls.behaviours.FPSCameraControl
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread

/**
 * This example demonstrates how to use the TargetArcBallBehaviour and how
 * to modify the default behaviour/key map of scenery.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ArcballTargetExample : SceneryDefaultApplication("ArcballTargetExample") {
    override fun init() {
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

        renderer = Renderer.createRenderer(hub, applicationName, scene, 1024, 1024)
        hub.add(SceneryElement.Renderer, renderer!!)
    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")
    }

    @Test override fun main() {
        super.main()
    }
}
