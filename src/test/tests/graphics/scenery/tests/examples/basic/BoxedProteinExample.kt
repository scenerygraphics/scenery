package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.Numerics
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread

/**
 * Example to demonstrate model loading with multiple lights,
 * combined with arcball controls.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BoxedProteinExample : SceneryDefaultApplication("BoxedProteinExample", windowWidth = 1280, windowHeight = 720) {
    override fun init() {
        val lightCount = 8

        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera()
        cam.position = GLVector(0.0f, 0.0f, 150.0f)
        cam.perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
        cam.active = true

        scene.addChild(cam)

        val boxes = (0..lightCount).map {
            Box(GLVector(0.5f, 0.5f, 0.5f))
        }

        val lights = (0..lightCount).map {
            PointLight()
        }

        boxes.mapIndexed { i, box ->
            box.material = Material()
            box.addChild(lights[i])
            scene.addChild(box)
        }

        lights.map {
            it.position = Numerics.randomVectorFromRange(3, -600.0f, 600.0f)
            it.emissionColor = Numerics.randomVectorFromRange(3, 0.0f, 1.0f)
            it.parent?.material?.diffuse = it.emissionColor
            it.intensity = Numerics.randomFromRange(0.01f, 100f)
            it.quadratic = 0.0f

            scene.addChild(it)
        }

        val hullbox = Box(GLVector(300.0f, 300.0f, 300.0f), insideNormals = true)
        with(hullbox) {
            position = GLVector(0.0f, 0.0f, 0.0f)

            material.ambient = GLVector(0.6f, 0.6f, 0.6f)
            material.diffuse = GLVector(0.4f, 0.4f, 0.4f)
            material.specular = GLVector(0.0f, 0.0f, 0.0f)
            material.doubleSided = true

            scene.addChild(this)
        }

        val orcMaterial = Material()
        orcMaterial.ambient = GLVector(0.8f, 0.8f, 0.8f)
        orcMaterial.diffuse = GLVector(0.5f, 0.5f, 0.5f)
        orcMaterial.specular = GLVector(0.1f, 0f, 0f)

        val orcMesh = Mesh()
        with(orcMesh) {
            readFromOBJ(getDemoFilesPath() + "/ORC6.obj")
            position = GLVector(0.0f, 50.0f, -50.0f)
            material = orcMaterial
            scale = GLVector(1.0f, 1.0f, 1.0f)
            updateWorld(true, true)
            name = "ORC6"

            material.ambient = GLVector(0.8f, 0.8f, 0.8f)
            material.diffuse = GLVector(0.5f, 0.5f, 0.5f)
            material.specular = GLVector(0.1f, 0f, 0f)

            children.forEach { it.material = material }

            scene.addChild(this)
        }

        var ticks: Int = 0

        thread {
            val step = 0.02f

            while (true) {
                boxes.mapIndexed {
                    i, box ->
                    val phi = Math.PI * 2.0f * ticks / 500.0f

                    box.position = GLVector(
                        Math.exp(i.toDouble()).toFloat() * 20 * Math.sin(phi).toFloat() + Math.exp(i.toDouble()).toFloat(),
                        step * ticks,
                        Math.exp(i.toDouble()).toFloat() * 20 * Math.cos(phi).toFloat() + Math.exp(i.toDouble()).toFloat())

                    box.children[0].position = box.position

                }

                ticks++

                Thread.sleep(10)
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

