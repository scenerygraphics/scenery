package graphics.scenery.tests.examples.bdv

import cleargl.GLVector
import com.sun.javafx.application.PlatformImpl
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.bdv.BDVVolume
import javafx.application.Platform
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import tpietzsch.example2.VolumeViewerOptions
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.math.max

/**
 * BDV Rendering Example
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BDVExample: SceneryBase("BDV Rendering example", 1280, 720) {
    var volume: BDVVolume? = null
    var currentCacheSize = 1024

    override fun init() {
        val latch = CountDownLatch(1)
        val files = ArrayList<String>()

        val fileFromProperty = System.getProperty("bdvXML")
        if(fileFromProperty != null) {
            files.add(fileFromProperty)
        } else {
            PlatformImpl.startup {  }
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

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight)
            active = true

//            position = GLVector(170.067406f, -138.45601f, -455.9538f)
//            rotation = Quaternion(-0.05395214f, 0.94574946f, -0.23843345f, 0.21400182f)

            scene.addChild(this)
        }

        val options = VolumeViewerOptions().maxCacheSizeInMB(1024)
        val v = BDVVolume(files.first(), options)
        v.name = "volume"
        v.colormap = "plasma"
        v.scale = GLVector(0.02f, 0.02f, 0.02f)
        scene.addChild(v)

        volume = v

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

        val nextTimePoint = ClickBehaviour { _, _ -> volume?.nextTimepoint() }
        val prevTimePoint = ClickBehaviour { _, _ -> volume?.previousTimepoint() }
        val tenTimePointsForward = ClickBehaviour { _, _ ->
            val current = volume?.currentTimepoint ?: 0
            volume?.goToTimePoint(current + 10)
        }
        val tenTimePointsBack = ClickBehaviour { _, _ ->
            val current = volume?.currentTimepoint ?: 0
            volume?.goToTimePoint(current - 10)
        }
        val moreCache = ClickBehaviour { _, _ ->
            currentCacheSize *= 2
            logger.info("Enlarging cache size to $currentCacheSize MB")
            volume?.resizeCache(currentCacheSize)
        }
        val lessCache = ClickBehaviour { _, _ ->
            currentCacheSize = max(currentCacheSize / 2, 256)
            logger.info("Cutting cache size to $currentCacheSize MB")
            volume?.resizeCache(max(currentCacheSize / 2, 256))
        }

        inputHandler?.addBehaviour("prev_timepoint", prevTimePoint)
        inputHandler?.addKeyBinding("prev_timepoint", "H")

        inputHandler?.addBehaviour("10_prev_timepoint", tenTimePointsBack)
        inputHandler?.addKeyBinding("10_prev_timepoint", "shift H")

        inputHandler?.addBehaviour("next_timepoint", nextTimePoint)
        inputHandler?.addKeyBinding("next_timepoint", "L")

        inputHandler?.addBehaviour("10_next_timepoint", tenTimePointsForward)
        inputHandler?.addKeyBinding("10_next_timepoint", "shift L")

        inputHandler?.addBehaviour("more_cache", moreCache)
        inputHandler?.addKeyBinding("more_cache", "9")

        inputHandler?.addBehaviour("less_cache", lessCache)
        inputHandler?.addKeyBinding("less_cache", "0")
    }

    @Test override fun main() {
        super.main()
    }
}
