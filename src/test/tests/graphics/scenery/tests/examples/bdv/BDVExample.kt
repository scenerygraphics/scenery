package graphics.scenery.tests.examples.bdv

import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import com.sun.javafx.application.PlatformImpl
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.Hololens
import graphics.scenery.volumes.bdv.BDVVolume
import javafx.application.Platform
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch

/**
 * Example that renders procedurally generated volumes on a [Hololens].
 * [bitsPerVoxel] can be set to 8 or 16, to generate Byte or UnsignedShort volumes.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BDVExample: SceneryBase("BDV Rendering example", 1280, 720) {
    override fun init() {
        val latch = CountDownLatch(1)
        val files = ArrayList<String>()
        PlatformImpl.startup {  }

        val fileFromProperty = System.getProperty("bdvXML")
        if(fileFromProperty != null) {
            files.add(fileFromProperty)
        } else {
            Platform.runLater {
                val chooser = FileChooser()
                chooser.title = "Open File"
                chooser.extensionFilters.add(FileChooser.ExtensionFilter("BigDataViewer XML", "*.xml"))
                val file = chooser.showOpenDialog(Stage())

                if (file != null) {
                    files.add(file.absolutePath)
                }
                latch.countDown()
            }

            latch.await()
        }

        if(files.size == 0) {
            throw IllegalStateException("You have to select a file, sorry.")
        }

        logger.info("Loading BDV XML from ${files.first()}")

        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight)
            active = true

            position = GLVector(170.067406f, -138.45601f, -455.9538f)
            rotation = Quaternion(-0.05395214f, 0.94574946f, -0.23843345f, 0.21400182f)

            scene.addChild(this)
        }

        val volume = BDVVolume(files.first())
        volume.name = "volume"
        volume.colormap = "plasma"
        volume.scale = GLVector(0.02f, 0.02f, 0.02f)
        scene.addChild(volume)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 50.0f
            scene.addChild(light)
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    @Test override fun main() {
        super.main()
    }
}
