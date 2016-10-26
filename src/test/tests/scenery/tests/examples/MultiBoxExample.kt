package scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import org.junit.Test
import scenery.*
import scenery.backends.Renderer
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
 */
class MultiBoxExample : SceneryDefaultApplication("MultiBoxExample") {
    override fun init() {
        try {
            renderer = Renderer.createRenderer(applicationName, scene, windowWidth, windowHeight)
            hub.add(SceneryElement.RENDERER, renderer!!)

            val cam: Camera = DetachedHeadCamera()

            fun rangeRandomizer(min: Float, max: Float): Float = min + (Math.random().toFloat() * ((max - min) + 1.0f))

            val WIDTH = 10.0
            val HEIGHT = 10.0

            val m = Mesh()
            val boxes = (0..1000).map {
                Box(GLVector(0.2f, 0.2f, 0.2f))
            }

            boxes.mapIndexed {
                s, box ->

                val k: Double = s % WIDTH;
                val j: Double = (s / WIDTH) % HEIGHT;
                val i: Double = s / (WIDTH * HEIGHT);

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
                it.position = GLVector(rangeRandomizer(-600.0f, 600.0f),
                        rangeRandomizer(-600.0f, 600.0f),
                        rangeRandomizer(-600.0f, 600.0f))
                it.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
                it.intensity = rangeRandomizer(0.01f, 1000f)
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
            cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)

            cam.projection = GLMatrix().setPerspectiveProjectionMatrix(
                    70.0f / 180.0f * Math.PI.toFloat(),
                    windowWidth.toFloat()/windowHeight.toFloat(), 0.1f, 1000.0f).invert()
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
