package graphics.scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.*
import org.junit.Test
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.ShaderPreference
import graphics.scenery.repl.REPL
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
 */
class MultiBoxInstancedExample : SceneryDefaultApplication("MultiBoxInstancedExample") {
    override fun init() {
        try {
            renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
            hub.add(SceneryElement.RENDERER, renderer!!)

            val cam: Camera = DetachedHeadCamera()

            cam.position = GLVector(0.0f, 0.0f, 0.0f)
            cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)

            cam.projection = GLMatrix().setPerspectiveProjectionMatrix(
                50.0f / 180.0f * Math.PI.toFloat(),
                windowWidth.toFloat() / windowHeight.toFloat(), 0.1f, 10000.0f)
            cam.active = true

            scene.addChild(cam)

            fun rangeRandomizer(min: Float, max: Float): Float = min + (Math.random().toFloat() * ((max - min) + 1.0f))

            val WIDTH = 15.0
            val HEIGHT = 15.0

            val m = Mesh()
            val b = Box(GLVector(0.2f, 0.2f, 0.2f))
            b.material = Material()
            b.material!!.diffuse = GLVector(1.0f, 1.0f, 1.0f)
            b.material!!.ambient = GLVector(1.0f, 1.0f, 1.0f)
            b.material!!.specular = GLVector(1.0f, 1.0f, 1.0f)
            b.instanceMaster = true
            b.name = "boxmaster"
            b.instancedProperties.put("ModelViewMatrix", {b.modelView })
            b.instancedProperties.put("ModelMatrix", {b.model })
            b.instancedProperties.put("MVP", {b.mvp })
            b.metadata.put(
                    "ShaderPreference",
                    ShaderPreference(
                            arrayListOf("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"),
                            HashMap(),
                            arrayListOf("DeferredShadingRenderer")))
            scene.addChild(b)

            (0..(WIDTH * HEIGHT * HEIGHT).toInt()).map {
                val p = Node("Parent of $it")

                val inst = Mesh()
                inst.name = "Box_$it"
                inst.instanceOf = b
                inst.material = b.material

                inst.instancedProperties.put("ModelViewMatrix", { inst.modelView })
                inst.instancedProperties.put("ModelMatrix", { inst.model })
                inst.instancedProperties.put("MVP", { inst.mvp })

                val k: Double = it % WIDTH;
                val j: Double = (it / WIDTH) % HEIGHT;
                val i: Double = it / (WIDTH * HEIGHT);

                p.position = GLVector(Math.floor(i).toFloat() * 3.0f, Math.floor(j).toFloat() * 3.0f, Math.floor(k).toFloat() * 3.0f)
                p.needsUpdate = true
                p.needsUpdateWorld = true
                p.addChild(inst)

                m.addChild(p)
                inst
            }

            scene.addChild(m)

            var lights = (0..10).map {
                PointLight()
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
