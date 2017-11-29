package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.volumes.Volume
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VolumeExample: SceneryBase("Volume Rendering example", 1280, 720) {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = GLVector(0.0f, 0.5f, 5.0f)
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight)
            active = true

            scene.addChild(this)
        }

        val shell = Box(GLVector(20.0f, 20.0f, 20.0f), insideNormals = true)
        shell.material.doubleSided = true
        shell.material.diffuse = GLVector(0.2f, 0.2f, 0.2f)
        shell.material.specular = GLVector.getNullVector(3)
        shell.material.ambient = GLVector.getNullVector(3)
        scene.addChild(shell)

        val volume = Volume()
        volume.colormap = "jet"
        scene.addChild(volume)

        val lights = (0..3).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(4.0f * i, 4.0f * i, 4.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 500.2f*(i+1)
            light.linear = 1.8f
            light.quadratic = 0.7f
            scene.addChild(light)
        }

        val chooser = JFileChooser()
        chooser.dialogTitle = "Choose volume file"
        chooser.approveButtonText = "Open volume file"
        UIManager.put("FileChooser.cancelButtonText", "Open example volume")
        UIManager.put("FileChooser.cancelButtonToolTipText", "Close this dialog and show an example volume provided by scenery.")
        SwingUtilities.updateComponentTreeUI(chooser)
        chooser.isMultiSelectionEnabled = true
        chooser.fileFilter = FileNameExtensionFilter("Volume files", "tif", "tiff", "raw", "czi")
        val result = chooser.showOpenDialog(null)

        val files = if(result == JFileChooser.CANCEL_OPTION || result == JFileChooser.ERROR_OPTION) {
            logger.info("Cancelled file selection, falling back to included demo volume")

            File(getDemoFilesPath() + "/volumes/box-iso/").listFiles().toList()
        } else {
            chooser.selectedFiles.toList()
        }

        val volumes = files.filter { it.isFile }.map { it.absolutePath }.sorted()
        logger.info("Got ${volumes.size} volumes: ${volumes.joinToString(", ")}")

        var currentVolume = 0
        fun nextVolume(): String {
            val v = volumes[currentVolume % (volumes.size)]
            currentVolume++

            return v
        }

        thread {
            while(!scene.initialized) { Thread.sleep(200) }

            val v = nextVolume()
            volume.readFrom(Paths.get(v), replace = true)

            logger.info("Got volume!")
        }

    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    @Test override fun main() {
        super.main()
    }
}
