package graphics.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.*
import org.junit.Test
import graphics.scenery.backends.Renderer
import graphics.scenery.repl.REPL
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
 */
class RungholtExample: SceneryDefaultApplication("BoxedProteinExample", windowWidth = 1280, windowHeight = 720) {
    override fun init() {
        try {
            val lightCount = 127

            renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
            hub.add(SceneryElement.RENDERER, renderer!!)

            val cam: Camera = DetachedHeadCamera()
            cam.position = GLVector(0.0f, 0.0f, 0.0f)
            cam.perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat(), nearPlaneLocation = 0.5f, farPlaneLocation = 1000.0f)
            cam.active = true

            scene.addChild(cam)
            fun rangeRandomizer(min: Float, max: Float): Float = min + (Math.random().toFloat() * ((max - min) + 1.0f))

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
                it.emissionColor = GLVector(rangeRandomizer(0.0f, 1.0f),
                    rangeRandomizer(0.0f, 1.0f),
                    rangeRandomizer(0.0f, 1.0f))
                it.parent?.material?.diffuse = it.emissionColor
                it.intensity = rangeRandomizer(0.01f, 10f)
                it.linear = 0.00f
                it.quadratic = 0.001f

                scene.addChild(it)
            }

            val hullbox = Box(GLVector(300.0f, 300.0f, 300.0f))
            hullbox.position = GLVector(0.0f, 0.0f, 0.0f)

            val hullboxMaterial = Material()
            hullboxMaterial.ambient = GLVector(0.6f, 0.6f, 0.6f)
            hullboxMaterial.diffuse = GLVector(0.4f, 0.4f, 0.4f)
            hullboxMaterial.specular = GLVector(0.0f, 0.0f, 0.0f)
            hullboxMaterial.doubleSided = true
            hullbox.material = hullboxMaterial

            val orcMaterial = Material()
            orcMaterial.ambient = GLVector(0.8f, 0.8f, 0.8f)
            orcMaterial.diffuse = GLVector(0.5f, 0.5f, 0.5f)
            orcMaterial.specular = GLVector(0.1f, 0f, 0f)

            val orcMesh = Mesh()
            orcMesh.readFromOBJ(System.getenv("SCENERY_DEMO_FILES") + "/rungholt.obj", useMTL = true)
            orcMesh.position = GLVector(0.0f, 0.0f, 0.0f)
            orcMesh.scale = GLVector(1.0f, 1.0f, 1.0f)
            orcMesh.updateWorld(true, true)
            orcMesh.name = "rungholt"

            scene.addChild(orcMesh)

            var ticks: Int = 0

            System.out.println(scene.children)

            thread {
                while (true) {
                    boxes.mapIndexed {
                        i, box ->
                        val phi = Math.PI * 2.0f * ticks / 2500.0f

                        box.position = GLVector(
                            -128.0f+18.0f*(i+1),
                            5.0f+i*5.0f,
                            (i+1) * 50 * Math.cos(phi+(i*0.2f)).toFloat())

                        box.children[0].position = box.position

                    }

                    ticks++


                    Thread.sleep(10)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    @Test override fun main() {
        super.main()
    }

}

