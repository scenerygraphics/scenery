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
class MultiBoxExample : SceneryDefaultApplication("MultiBoxExample") {
    override fun init() {
        try {
            renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
            hub.add(SceneryElement.RENDERER, renderer!!)

            val cam: Camera = DetachedHeadCamera()

            val WIDTH = 15.0
            val HEIGHT = 15.0

            val m = Mesh()
            val boxes = (0..1000).map {
                Box(GLVector(0.2f, 0.2f, 0.2f))
            }

            boxes.mapIndexed {
                s, box ->

                val k: Double = s % WIDTH
                val j: Double = (s / WIDTH) % HEIGHT
                val i: Double = s / (WIDTH * HEIGHT)

                box.position = GLVector(Math.floor(i).toFloat() * 3.0f, Math.floor(j).toFloat() * 3.0f, Math.floor(k).toFloat() * 3.0f)

                m.addChild(box)
            }

            scene.addChild(m)

            var lights = (0..10).map {
                PointLight()
            }

            boxes.mapIndexed { i, box ->
                box.material = Material()
            }

            lights.map {
                it.position = Numerics.randomVectorFromRange(3, -600.0f, 600.0f)
                it.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
                it.intensity = Numerics.randomFromRange(0.01f, 1000f)
                it.linear = 0.1f;
                it.quadratic = 0.1f;

                scene.addChild(it)
            }

            val hullbox = Box(GLVector(100.0f, 100.0f, 100.0f))
            hullbox.position = GLVector(0.0f, 0.0f, 0.0f)
            val hullboxM = Material()
            hullboxM.ambient = GLVector(0.6f, 0.6f, 0.6f)
            hullboxM.diffuse = GLVector(0.4f, 0.4f, 0.4f)
            hullboxM.specular = GLVector(0.0f, 0.0f, 0.0f)
            hullboxM.doubleSided = true
            hullbox.material = hullboxM

            scene.addChild(hullbox)

            cam.position = GLVector(0.0f, 0.0f, 0.0f)
            cam.perspectiveCamera(60.0f, 1.0f*windowWidth, 1.0f*windowHeight, 1.0f, 1000.0f)

            cam.active = true

            scene.addChild(cam)

            var ticks: Int = 0

            thread {
                val step = 0.02f

                while (true) {
                    lights.mapIndexed {
                        i, light ->
                        val phi = Math.PI * 2.0f * ticks / 500.0f

                        light.position = GLVector(
                                Math.exp(i.toDouble()).toFloat() * 10 * Math.sin(phi).toFloat() + Math.exp(i.toDouble()).toFloat(),
                                step * ticks,
                                Math.exp(i.toDouble()).toFloat() * 10 * Math.cos(phi).toFloat() + Math.exp(i.toDouble()).toFloat())

                    }

                    ticks++

                    m.rotation.rotateByEuler(0.001f, 0.001f, 0.0f)
                    m.needsUpdate = true

                    Thread.sleep(10)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    @Test override fun main() {
        super.main()
    }

}
