package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Line
import graphics.scenery.attribute.material.Material
import org.jdom2.internal.SystemProperty
import org.joml.Vector4f
import java.util.Properties
import kotlin.concurrent.thread

/**
 * Simple example to demonstrate the drawing of 3D lines.
 *
 * This example will draw a nicely illuminated bundle of lines using
 * the [Line] class. The line's width will oscillate while 3 lights
 * circle around the scene.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class LineExample : SceneryBase("LineExample", wantREPL = false) {
    protected var lineAnimating = true

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val hull = Box(Vector3f(50.0f, 50.0f, 50.0f), insideNormals = true)
        hull.material {
            diffuse = Vector3f(0.2f, 0.2f, 0.2f)
            cullingMode = Material.CullingMode.Front
        }
        scene.addChild(hull)

        val line = Line(transparent = false)
        // Topology when simple = false -> Line_Strip_Adjecency
        // First Point 0 and last Point n-1 only available in Geometry shader. 'Lines' between
        // Point 0 and Point 1 & Point n-2 and Point n-1 will not be rendered.
        // So: Point 1 here defines the start of the line, and the first Line will be rendered between
        // Point 1 and Point 2 AFTER another point has been added, going from (0, 0, 0) to (5, 5, 5)
        line.addPoint(Vector3f(-5.0f, -5.0f, -5.0f))
        line.addPoint(Vector3f(0.0f, 0.0f, 0.0f))
        line.addPoint(Vector3f(5.0f, 5.0f, 5.0f))

        line.startColor = Vector4f(1.0f, 0.0f, 0.0f, 1.0f)
        line.lineColor = Vector4f(0.0f, 1.0f, 0.0f, 1.0f)
        line.endColor = Vector4f(0.0f, 0.0f, 1.0f, 1.0f)

        line.material {
            ambient = Vector3f(1.0f, 0.0f, 0.0f)
            diffuse = Vector3f(1.0f, 1.0f, 1.0f)
            specular = Vector3f(1.0f, 1.0f, 1.0f)
        }
        line.spatial {
            position = Vector3f(0.0f, 0.0f, 0.0f)
        }

        line.edgeWidth = 1f
        scene.addChild(line)


        val sphereS = Sphere(0.3f)
        sphereS.material {
            ambient = Vector3f(1.0f, 0.0f, 0.0f)
            diffuse = Vector3f(1.0f, 0.0f, 0.0f)
            specular = Vector3f(1.0f, 1.0f, 1.0f)
        }
        sphereS.spatial{
            position = Vector3f(-5.0f, -5.0f, -5.0f)
        }
        val sphereZ = Sphere(0.3f)
        sphereZ.material {
            ambient = Vector3f(1.0f, 0.0f, 0.0f)
            diffuse = Vector3f(0.0f, 1.0f, 0.0f)
            specular = Vector3f(1.0f, 1.0f, 1.0f)
        }
        sphereZ.spatial{
            position = Vector3f(0.0f, 0.0f, 0.0f)
        }
        val sphereE = Sphere(0.3f)
        sphereE.material {
            ambient = Vector3f(1.0f, 0.0f, 0.0f)
            diffuse = Vector3f(0.0f, 0.0f, 1.0f)
            specular = Vector3f(1.0f, 1.0f, 1.0f)
        }
        sphereE.spatial{
            position = Vector3f(5.0f, 5.0f, 5.0f)
        }
        //scene.addChild(sphereS)
        //scene.addChild(sphereZ)
        //scene.addChild(sphereE)


        val lights = (0 until 2).map {
            val l = PointLight(radius = 10.0f)
            l.intensity = 2.0f
            l.emissionColor = Random.random3DVectorFromRange(0.2f, 0.8f)

            scene.addChild(l)
            l
        }

        val cam: Camera = DetachedHeadCamera()
        cam.spatial {
            position = Vector3f(0.0f, 0.0f, 15.0f)
        }
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        cam.target = Vector3f(0.0f, 0.0f, 0.0f)

        scene.addChild(cam)

        thread {
            while (true) {
                val t = runtime/100
                if (lineAnimating) {
                    line.addPoint(Random.random3DVectorFromRange(-5.0f, 5.0f))
                    line.edgeWidth = 0.01f * Math.sin(t * Math.PI / 50).toFloat() + 0.015f
                }

                Thread.sleep(100)
            }
        }

        thread {
            while(true) {
                val t = runtime/100
                lights.forEachIndexed { i, pointLight ->
                    pointLight.spatial {
                        position = Vector3f(
                            3.0f*Math.sin(2*i*Math.PI/3.0f+t*Math.PI/50).toFloat(),
                            0.0f,
                            -3.0f*Math.cos(2*i*Math.PI/3.0f+t*Math.PI/50).toFloat())
                    }
                }

                Thread.sleep(20)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            LineExample().main()
        }
    }
}
