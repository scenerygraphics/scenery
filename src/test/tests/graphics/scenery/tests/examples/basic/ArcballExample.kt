package graphics.scenery.tests.examples.basic

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.Numerics
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread

/**
 * This example demonstrates how to use the TargetArcBallBehaviour and how
 * to modify the default behaviour/key map of scenery, and also manually
 * trigger behaviours. See also [SceneryDefaultApplication.setupCameraModeSwitching].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ArcballExample : SceneryDefaultApplication("ArcballExample") {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, 1024, 1024)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(70.0f, windowWidth.toFloat(), windowHeight.toFloat())

            position = GLVector(0.0f, 0.0f, 2.5f)
            view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)
            active = true

            scene.addChild(this)
        }

        val box = Box(GLVector(1.0f, 1.0f, 1.0f))

        with(box) {
            box.position = GLVector(0.0f, 0.0f, 0.0f)

            material.ambient = GLVector(1.0f, 0.0f, 0.0f)
            material.diffuse = GLVector(0.0f, 1.0f, 0.0f)
            material.specular = GLVector(1.0f, 1.0f, 1.0f)
            material.textures.put("diffuse", TexturedCubeExample::class.java.getResource("textures/helix.png").file)

            scene.addChild(this)
        }

        val lights = (0..2).map {
            PointLight()
        }.map { light ->
            light.position = Numerics.randomVectorFromRange(3, -5.0f, 5.0f)
            light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
            light.intensity = Numerics.randomFromRange(50.0f, 150.0f)
            light
        }

        lights.forEach(scene::addChild)

        thread {
            while (true) {
                box.rotation.rotateByAngleY(0.01f)
                box.needsUpdate = true

                Thread.sleep(20)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")

        // switch to arcball mode by manually triggering the behaviour
        (inputHandler?.getBehaviour("toggle_control_mode") as ClickBehaviour).click(0, 0)
    }

    @Test override fun main() {
        super.main()
    }
}
