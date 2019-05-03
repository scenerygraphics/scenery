package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import com.sun.javafx.application.PlatformImpl
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.numerics.Random
import javafx.application.Platform
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.junit.Test
import java.util.concurrent.CountDownLatch



/**
 * Example to load localisation microscopy files.
 * Files have to have the format X\tY\tZ\tdX\tdY
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class LocalisationExample : SceneryBase("Localisation Microscopy Rendering example", 1280, 720) {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        val latch = CountDownLatch(1)
        val files = ArrayList<String>()
        PlatformImpl.startup {  }

        Platform.runLater {
            val chooser = FileChooser()
            chooser.title = "Open File"
            chooser.extensionFilters.addAll(FileChooser.ExtensionFilter("CSV/TSV files", "*.txt", "*.csv", "*.tsv", "*.xls"))
            val file = chooser.showOpenMultipleDialog(Stage())

            if(file != null) {
                files.addAll(file.map { it.absolutePath })
            }
            latch.countDown()
        }

        latch.await()
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = GLVector(0.0f, 0.5f, 5.0f)
            perspectiveCamera(50.0f, 1.0f * windowWidth, 1.0f * windowHeight)
            active = true

            scene.addChild(this)
        }

        val shell = Box(GLVector(20.0f, 20.0f, 20.0f), insideNormals = true)
        shell.material.cullingMode = Material.CullingMode.Front
        shell.material.diffuse = GLVector(0.9f, 0.9f, 0.9f)
        shell.material.specular = GLVector.getNullVector(3)
        shell.material.ambient = GLVector.getNullVector(3)
        scene.addChild(shell)

        if(System.getProperty("datasets") != null) {
            files.clear()
            files.addAll(System.getProperty("datasets").split(";").toList())
        }

        // channel colors for red, green, blue
        val channelColors = arrayOf(
            GLVector(0.1f, 0.8f, 0.0f),
            GLVector(1.0f, 0.04f, 0.04f),
            GLVector(0.1f, 0.1f, 0.8f))

        val datasets = files.mapIndexed { i, file ->
            val dataset = PointCloud()
            dataset.readFromPALM(file)
            dataset.material.diffuse = channelColors.getOrElse(i, { Random.randomVectorFromRange(3, 0.1f, 0.9f) })
            dataset.fitInto(5.0f)
            scene.addChild(dataset)

            dataset
        }

        val light = PointLight(radius = 40.0f)
        light.intensity = 500.0f
        light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
        scene.addChild(light)


    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    @Test override fun main() {
        super.main()
    }
}
