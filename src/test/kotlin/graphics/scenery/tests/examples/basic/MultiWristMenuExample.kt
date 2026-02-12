package graphics.scenery.tests.examples.basic

import graphics.scenery.Box
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.PointLight
import graphics.scenery.SceneryBase
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.ui.Button
import graphics.scenery.ui.MultiWristMenu
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Simple example to test out [MultiWristMenu]s.
 * @author Samuel Pantze
 * */
class MultiWristMenuExample : SceneryBase("MultiWristMenuExample") {
    override fun init() {
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        )

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 1.0f)
            }
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }

        val box = Box(Vector3f(0.01f))

        scene.addChild(box)

        val menu =
            MultiWristMenu(
                box,
                columnScale = 0.1f,
                columnBasePosition = Vector3f(0f),
                columnRotation = Quaternionf().rotateXYZ(0f, 0f, 0.5f)
            )
        menu.addColumn("Default")
        menu.addButton("Default", "up", {})
        menu.addButton("Default", "middle", {})
        val buttonLeft = Button("left", command = {})
        val buttonRight = Button("right", command = {})
        menu.addRow("Default", buttonLeft, buttonRight)
        menu.addButton("Default", "down", {})

        val lights = (0..5).map {
            PointLight(radius = 100.0f)
        }.map {
            it.spatial {
                position = Random.random3DVectorFromRange(-5.0f, 5.0f)
            }
            it.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            it.intensity = Random.randomFromRange(0.5f, 2f)
            it
        }

        lights.forEach {
            scene.addChild(it)
        }

    }


    override fun inputSetup() {
        super.inputSetup()
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MultiWristMenuExample().main()
        }
    }
}
