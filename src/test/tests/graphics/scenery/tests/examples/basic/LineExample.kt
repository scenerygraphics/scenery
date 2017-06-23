package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import org.junit.Test
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
class LineExample : SceneryDefaultApplication("LineExample") {
    protected var lineAnimating = true

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val hull = Box(GLVector(50.0f, 50.0f, 50.0f))
        val hullmaterial = Material()
        hullmaterial.diffuse = GLVector(0.2f, 0.2f, 0.2f)
        hullmaterial.doubleSided = true
        hull.material = hullmaterial
        scene.addChild(hull)

        val linematerial = Material()
        linematerial.ambient = GLVector(1.0f, 0.0f, 0.0f)
        linematerial.diffuse = GLVector(0.0f, 1.0f, 0.0f)
        linematerial.specular = GLVector(1.0f, 1.0f, 1.0f)

        val line = Line()
        line.addPoint(GLVector(-1.0f, -1.0f, -1.0f))
        line.addPoint(GLVector(2.0f, 0.0f, 2.0f))
        line.material = linematerial
        line.position = GLVector(0.0f, 0.0f, 0.0f)

        scene.addChild(line)

        val colors = arrayOf(
            GLVector(1.0f, 0.0f, 0.0f),
            GLVector(0.0f, 1.0f, 0.0f),
            GLVector(0.0f, 0.0f, 1.0f)
        )

        val lights = (0..2).map {
            val l = PointLight()
            l.intensity = 200.0f
            l.emissionColor = colors[it]

            scene.addChild(l)
            l
        }

        val cam: Camera = DetachedHeadCamera()
        cam.position = GLVector(0.0f, 0.0f, 15.0f)
        cam.perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
        cam.active = true

        scene.addChild(cam)

        thread {
            var t = 0
            while(true) {
                if(lineAnimating) {
                    line.addPoint(GLVector(10.0f * Math.random().toFloat() - 5.0f, 10.0f * Math.random().toFloat() - 5.0f, 10.0f * Math.random().toFloat() - 5.0f))
                    line.edgeWidth = 0.03f * Math.sin(t * Math.PI / 50).toFloat() + 0.004f
                }

                Thread.sleep(100)

                lights.forEachIndexed { i, pointLight ->
                    pointLight.position = GLVector(0.0f, 15.0f*Math.sin(2*i*Math.PI/3.0f+t*Math.PI/50).toFloat(), -15.0f*Math.cos(2*i*Math.PI/3.0f+t*Math.PI/50).toFloat())
                }

                t++
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")
    }

    @Test override fun main() {
        super.main()
    }
}
