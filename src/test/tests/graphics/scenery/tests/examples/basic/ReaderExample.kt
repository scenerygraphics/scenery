package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.volumes.Volume
import org.junit.Test
import org.scijava.Context
import org.scijava.ui.UIService
import org.scijava.widget.FileWidget
import java.nio.file.Paths
import kotlin.concurrent.thread

/**
 * Example for loading OBJ and STL files.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ReaderExample : SceneryBase("ReaderExample", 1280, 720) {
    override fun init() {
        val files = ArrayList<String>()

        val c = Context()
        val ui = c.getService(UIService::class.java)
        val file = ui.chooseFile(null, FileWidget.OPEN_STYLE)
        files.add(file.absolutePath)

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val b = Box(GLVector(50.0f, 0.2f, 50.0f))
        b.position = GLVector(0.0f, -1.0f, 0.0f)
        b.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        scene.addChild(b)

        val tetrahedron = listOf(
            GLVector(1.0f, 0f, -1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(-1.0f,0f,-1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(0.0f,1.0f,1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(0.0f,-1.0f,1.0f/Math.sqrt(2.0).toFloat()))

        val lights = (0 until 4).map { PointLight(radius = 50.0f) }

        val n: Node = if(files.isNotEmpty()) {
            when {
                files.first().endsWith(".tiff") || files.first().endsWith(".tif") -> {
                    val v = Volume()
                    v.readFrom(Paths.get(files.first()))

                    v
                }
                files.first().endsWith(".raw") -> {
                    val v = Volume()
                    v.readFromRaw(Paths.get(files.first()))

                    v
                }

                else -> {
                    val m = Mesh()
                    m.readFrom(files.first())

                    m
                }
            }
        } else {
            throw IllegalStateException("No file selected")
        }

        n.fitInto(6.0f, scaleUp = false)

        scene.addChild(n)

        val bg = BoundingGrid()
        bg.node = n

        tetrahedron.mapIndexed { i, position ->
            lights[i].position = position * 5.0f
            lights[i].emissionColor = Random.randomVectorFromRange(3, 0.8f, 1.0f)
            lights[i].intensity = 0.5f
            scene.addChild(lights[i])
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true

            scene.addChild(this)
        }

        thread {
            while(!n.initialized) {
                Thread.sleep(200)
            }

            n.putAbove(GLVector(0.0f, -0.3f, 0.0f))
        }
    }

    override fun inputSetup() {
        super.inputSetup()
        setupCameraModeSwitching()
    }

    @Test override fun main() {
        super.main()
    }
}
