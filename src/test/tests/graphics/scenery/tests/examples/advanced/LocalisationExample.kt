package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.numerics.Random
import org.junit.Test
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Example to load localisation microscopy files.
 * Files have to have the format X\tY\tZ\tdX\tdY
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class LocalisationExample : SceneryBase("Localisation Microscopy Rendering example", 1280, 720) {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = GLVector(0.0f, 0.5f, 5.0f)
            perspectiveCamera(50.0f, 1.0f * windowWidth, 1.0f * windowHeight)
            active = true

            scene.addChild(this)
        }

        val shell = Box(GLVector(20.0f, 20.0f, 20.0f), insideNormals = true)
        shell.material.doubleSided = true
        shell.material.diffuse = GLVector(0.1f, 0.1f, 0.1f)
        shell.material.specular = GLVector.getNullVector(3)
        shell.material.ambient = GLVector.getNullVector(3)
        scene.addChild(shell)

        val files = if(System.getProperty("datasets") == null) {
            val chooser = JFileChooser()
            chooser.isMultiSelectionEnabled = true
            chooser.fileFilter = FileNameExtensionFilter("CSV/TSV files", "txt", "csv", "tsv", "xls")
            chooser.showOpenDialog(null)

            chooser.selectedFiles.map { it.absolutePath }.toList()
        } else {
            System.getProperty("datasets").split(";").toList()
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
            dataset.centerOn(dataset.position)
            dataset.fitInto(10.0f)
            scene.addChild(dataset)

            dataset
        }

        val lights = (0..3).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(4.0f * i, 4.0f * i, 4.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 50.2f
            light.linear = 1.8f
            light.quadratic = 0.7f
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
