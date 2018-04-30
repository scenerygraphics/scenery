package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import com.sun.javafx.application.PlatformImpl
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import javafx.application.Platform
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.junit.Test
import java.util.concurrent.CountDownLatch

/**
 * Example for loading OBJ and STL files.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ReadModelExample : SceneryBase("ReadModelExample", 1280, 720) {
    override fun init() {
        val latch = CountDownLatch(1)
        val files = ArrayList<String>()
        PlatformImpl.startup {  }

        Platform.runLater {
            val chooser = FileChooser()
            chooser.title = "Open File"
            chooser.extensionFilters.add(FileChooser.ExtensionFilter("OBJ 3D models", "*.obj"))
            chooser.extensionFilters.add(FileChooser.ExtensionFilter("STL 3D models", "*.stl"))
            val file = chooser.showOpenDialog(Stage())

            if(file != null) {
                files.add(file.absolutePath)
            }
            latch.countDown()
        }

        latch.await()

        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

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

        if(files.isNotEmpty()) {
            val m = Mesh()
            m.readFrom(files.first())
            m.fitInto(10.0f, scaleUp = false)

            scene.addChild(m)
        }

        tetrahedron.mapIndexed { i, position ->
            lights[i].position = position * 5.0f
            lights[i].emissionColor = Random.randomVectorFromRange(3, 0.8f, 1.0f)
            lights[i].intensity = 200.2f
            scene.addChild(lights[i])
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true

            scene.addChild(this)
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
