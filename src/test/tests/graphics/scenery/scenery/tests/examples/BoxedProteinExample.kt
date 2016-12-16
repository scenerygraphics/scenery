package graphics.scenery.scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.scenery.*
import org.junit.Test
import graphics.scenery.scenery.backends.Renderer
import graphics.scenery.scenery.repl.REPL
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
 */
class BoxedProteinExample : SceneryDefaultApplication("BoxedProteinExample", windowWidth = 1280, windowHeight = 720) {
    override fun init() {
        try {
            renderer = Renderer.createRenderer(applicationName, scene, windowWidth, windowHeight)
            hub.add(SceneryElement.RENDERER, renderer!!)

            val cam: Camera = DetachedHeadCamera()
            cam.position = GLVector(0.0f, 0.0f, 0.0f)
            cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)

            cam.projection = GLMatrix().setPerspectiveProjectionMatrix(
                50.0f / 180.0f * Math.PI.toFloat(),
                windowWidth.toFloat()/windowHeight.toFloat(), 0.1f, 1000.0f).invert()
            cam.active = true

            scene.addChild(cam)
            fun rangeRandomizer(min: Float, max: Float): Float = min + (Math.random().toFloat() * ((max - min) + 1.0f))

            var boxes = (0..2).map {
                Box(GLVector(0.5f, 0.5f, 0.5f))
            }

            var lights = (0..2).map {
                PointLight()
            }

            boxes.mapIndexed { i, box ->
                box.material = Material()
                box.addChild(lights[i])
                scene.addChild(box)
            }

            lights.map {
                it.position = GLVector(rangeRandomizer(-600.0f, 600.0f),
                        rangeRandomizer(-600.0f, 600.0f),
                        rangeRandomizer(-600.0f, 600.0f))
                it.emissionColor = GLVector(rangeRandomizer(0.0f, 1.0f),
                        rangeRandomizer(0.0f, 1.0f),
                        rangeRandomizer(0.0f, 1.0f))
                it.parent?.material?.diffuse = it.emissionColor
                it.intensity = rangeRandomizer(0.01f, 500f)
                it.linear = 0.01f;
                it.quadratic = 0.01f;

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

            val mesh = Mesh()
            val meshM = Material()
            meshM.ambient = GLVector(0.8f, 0.8f, 0.8f)
            meshM.diffuse = GLVector(0.5f, 0.5f, 0.5f)
            meshM.specular = GLVector(0.1f, 0f, 0f)

            mesh.readFromOBJ(System.getenv("SCENERY_DEMO_FILES") + "/ORC6.obj")
            mesh.position = GLVector(0.1f, 0.1f, 0.1f)
            mesh.material = meshM
            mesh.scale = GLVector(1.0f, 1.0f, 1.0f)
            mesh.updateWorld(true, true)
            mesh.name = "ORC6"
            mesh.children.forEach { it.material = meshM }

            scene.addChild(mesh)



            var ticks: Int = 0

            System.out.println(scene.children)

            thread {
                var reverse = false
                val step = 0.02f

                while (true) {
                    boxes.mapIndexed {
                        i, box ->
                        //                                light.position.set(i % 3, step*10 * ticks)
                        val phi = Math.PI * 2.0f * ticks / 500.0f

                        box.position = GLVector(
                                Math.exp(i.toDouble()).toFloat() * 20 * Math.sin(phi).toFloat() + Math.exp(i.toDouble()).toFloat(),
                                step * ticks,
                                Math.exp(i.toDouble()).toFloat() * 20 * Math.cos(phi).toFloat() + Math.exp(i.toDouble()).toFloat())

                        box.children[0].position = box.position

                    }

                    if (ticks >= 5000 && reverse == false) {
                        reverse = true
                    }
                    if (ticks <= 0 && reverse == true) {
                        reverse = false
                    }

                    if (reverse) {
                        ticks--
                    } else {
                        ticks++
                    }

//                            mesh.children[0].rotation.rotateByAngleX(0.001f)
//                            mesh.children[0].updateWorld(true, true)

                    Thread.sleep(10)
                }
            }

            repl = REPL(scene, renderer!!)
            repl?.start()
            repl?.showConsoleWindow()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    @Test override fun main() {
        super.main()
    }

}

