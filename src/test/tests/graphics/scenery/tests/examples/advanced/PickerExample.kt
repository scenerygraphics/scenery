package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.behaviours.SelectCommand
import graphics.scenery.numerics.Random
import org.junit.Test
import kotlin.concurrent.thread

/**
 * Test for [SelectCommand], produces a lot of (clickable) spheres, that
 * wiggle when selected by double-click.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class PickerExample: SceneryBase("PickerExample", wantREPL = true) {
    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        for(i in 0 until 200) {
            val s = Sphere(Random.randomFromRange(0.04f, 0.2f), 10)
            s.position = Random.randomVectorFromRange(3, -5.0f, 5.0f)
            scene.addChild(s)
        }

        val box = Box(GLVector(10.0f, 10.0f, 10.0f), insideNormals = true)
        box.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        box.material.cullingMode = Material.CullingMode.Front
        scene.addChild(box)

        val light = PointLight(radius = 15.0f)
        light.position = GLVector(0.0f, 0.0f, 2.0f)
        light.intensity = 100.0f
        light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512.0f, 512.0f)
            active = true

            scene.addChild(this)
        }
    }

    override fun inputSetup() {
        super.inputSetup()

        val wiggle: (List<Scene.RaycastResult>) -> Unit = { result ->
            result.firstOrNull()?.let { nearest ->
                val originalPosition = nearest.node.position.clone()
                thread {
                    for(i in 0 until 200) {
                        nearest.node.position = originalPosition + Random.randomVectorFromRange(3, -0.05f, 0.05f)
                        Thread.sleep(2)
                    }
                }
            }
        }

        renderer?.let { r ->
            inputHandler?.addBehaviour("select", SelectCommand("select", r, scene,
                { scene.findObserver() }, action = wiggle))
            inputHandler?.addKeyBinding("select", "double-click button1")
        }
    }

    @Test override fun main() {
        super.main()
    }
}
