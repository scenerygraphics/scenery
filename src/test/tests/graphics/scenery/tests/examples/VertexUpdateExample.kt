package graphics.scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.*
import org.junit.Test
import graphics.scenery.backends.Renderer
import graphics.scenery.repl.REPL
import java.nio.FloatBuffer
import java.util.*
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

class VertexUpdateExample : SceneryDefaultApplication("VertexUpdateExample") {

    override fun init() {
        renderer = Renderer.createRenderer(applicationName,
               scene, 512, 512)
        hub.add(SceneryElement.RENDERER, renderer!!)

        var sphere = Sphere(2.0f, 50)

        var material = Material()
        material.ambient = GLVector(1.0f, 1.0f, 1.0f)
        material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        material.specular = GLVector(1.0f, 1.0f, 1.0f)
        material.doubleSided = true

        sphere.position = GLVector(0.0f, 0.0f, 0.0f)
        sphere.material = material

        scene.addChild(sphere)

        var lights = (0..2).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 100f * (i + 1);
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        cam.position = GLVector(0.0f, 0.0f, -5.0f)
        cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)
        cam.projection = GLMatrix()
                .setPerspectiveProjectionMatrix(
                        70.0f / 180.0f * Math.PI.toFloat(),
                        renderer!!.window.width.toFloat() / renderer!!.window.height.toFloat(), 0.1f, 1000.0f)
                .invert()
        cam.active = true

        scene.addChild(cam)

        var ticks = 0
        thread {
            while (true) {
                sphere.rotation.rotateByAngleY(0.01f)
                sphere.needsUpdate = true
                ticks++

                val vbuffer = ArrayList<Float>()
                val nbuffer = ArrayList<Float>()

                val segments = 50
                val radius = 2.0f
                for (i in 0..segments) {
                    val lat0: Float = Math.PI.toFloat() * (-0.5f + (i.toFloat() - 1.0f) / segments.toFloat());
                    val lat1: Float = Math.PI.toFloat() * (-0.5f + i.toFloat() / segments.toFloat());

                    val z0 = Math.sin(lat0.toDouble()).toFloat()
                    val z1 = Math.sin(lat1.toDouble()).toFloat()

                    val zr0 = Math.cos(lat0.toDouble()).toFloat()
                    val zr1 = Math.cos(lat1.toDouble()).toFloat()

                    for (j: Int in 1..segments) {
                        val lng = 2 * Math.PI.toFloat() * (j - 1) / segments
                        val x = Math.cos(lng.toDouble()).toFloat()
                        val y = Math.sin(lng.toDouble()).toFloat()
                        var r = radius

                        if (j % 10 == 0) {
                            r = radius + Math.sin(ticks / 100.0).toFloat()
                        }
                        vbuffer.add(x * zr0 * r)
                        vbuffer.add(y * zr0 * r)
                        vbuffer.add(z0 * r)

                        vbuffer.add(x * zr1 * r)
                        vbuffer.add(y * zr1 * r)
                        vbuffer.add(z1 * r)

                        nbuffer.add(x)
                        nbuffer.add(y)
                        nbuffer.add(z0)

                        nbuffer.add(x)
                        nbuffer.add(y)
                        nbuffer.add(z1)
                    }
                }

                sphere.vertices = FloatBuffer.wrap(vbuffer.toFloatArray())
                sphere.normals = FloatBuffer.wrap(nbuffer.toFloatArray())
                sphere.recalculateNormals()

                sphere.dirty = true

                Thread.sleep(20)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
