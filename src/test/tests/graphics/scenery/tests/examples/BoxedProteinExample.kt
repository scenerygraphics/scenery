package graphics.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.Numerics
import org.junit.Test
import java.io.IOException
import kotlin.concurrent.thread

/**
* <Description>
*
* @author Ulrik Günther <hello@ulrik.is>
*/
class BoxedProteinExample : SceneryDefaultApplication("BoxedProteinExample", windowWidth = 1280, windowHeight = 720) {
    override fun init() {
        try {
            val lightCount = 32

            renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
            hub.add(SceneryElement.Renderer, renderer!!)

            val cam: Camera = DetachedHeadCamera()
            cam.position = GLVector(0.0f, 0.0f, 0.0f)
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
                it.linear = 0.01f
                it.quadratic = 0.0f

                scene.addChild(it)
            }

            val hullbox = Box(GLVector(300.0f, 300.0f, 300.0f), insideNormals = true)
            hullbox.position = GLVector(0.0f, 0.0f, 0.0f)

            val hullboxMaterial = Material()
            hullboxMaterial.ambient = GLVector(0.6f, 0.6f, 0.6f)
            hullboxMaterial.diffuse = GLVector(0.4f, 0.4f, 0.4f)
            hullboxMaterial.specular = GLVector(0.0f, 0.0f, 0.0f)
            hullboxMaterial.doubleSided = true
            hullbox.material = hullboxMaterial

            scene.addChild(hullbox)

            val orcMaterial = Material()
            orcMaterial.ambient = GLVector(0.8f, 0.8f, 0.8f)
            orcMaterial.diffuse = GLVector(0.5f, 0.5f, 0.5f)
            orcMaterial.specular = GLVector(0.1f, 0f, 0f)

            val orcMesh = Mesh()
            orcMesh.readFromOBJ(getDemoFilesPath() + "/ORC6.obj")
            orcMesh.position = GLVector(0.0f, 50.0f, -50.0f)
            orcMesh.material = orcMaterial
            orcMesh.scale = GLVector(1.0f, 1.0f, 1.0f)
            orcMesh.updateWorld(true, true)
            orcMesh.name = "ORC6"
            orcMesh.children.forEach { it.material = orcMaterial }

            scene.addChild(orcMesh)

            var ticks: Int = 0

            System.out.println(scene.children)

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
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    @Test override fun main() {
        super.main()
    }

}

