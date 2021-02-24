package graphics.scenery.tests.examples.basic

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.behaviours.MouseDrag
import graphics.scenery.numerics.Random
import graphics.scenery.tests.examples.advanced.PickerExample
import org.joml.Vector3f

/**
 * Drag nodes roughly along the viewplane axis by mouse.
 *
 * Most of the setup is copied from [PickerExample].
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
class MouseDragExample : SceneryBase("MouseDragExample") {

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        for (i in 0 until 200) {
            val s = Icosphere(Random.randomFromRange(0.04f, 0.2f), 2)
            s.position = Random.random3DVectorFromRange(-5.0f, 5.0f)
            scene.addChild(s)
        }

        val box = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        box.material.diffuse = Vector3f(1.0f, 1.0f, 1.0f)
        box.material.cullingMode = Material.CullingMode.Front
        scene.addChild(box)

        val light = PointLight(radius = 15.0f)
        light.position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 1.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = Vector3f(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }
    }

    override fun inputSetup() {
        super.inputSetup()

        renderer?.let { r ->
            inputHandler?.addBehaviour(
                "dragObject", MouseDrag("dragObject",
                    { scene.findObserver() }, debugRaycast = false
                )
            )
            inputHandler?.addKeyBinding("dragObject", "R")
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MouseDragExample().main()
        }
    }
}
