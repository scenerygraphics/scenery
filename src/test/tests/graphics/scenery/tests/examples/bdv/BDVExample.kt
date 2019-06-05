package graphics.scenery.tests.examples.bdv

import cleargl.GLVector
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.PointLight
import graphics.scenery.SceneryBase
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.bdv.BDVVolume
import org.junit.Test
import org.scijava.Context
import org.scijava.ui.UIService
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.widget.FileWidget
import tpietzsch.example2.VolumeViewerOptions
import java.util.*
import kotlin.math.max

/**
 * BDV Rendering Example
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BDVExample: SceneryBase("BDV Rendering example", 1280, 720) {
    var volume: BDVVolume? = null
    var currentCacheSize = 512

    override fun init() {
        val files = ArrayList<String>()

        val fileFromProperty = System.getProperty("bdvXML")
        if(fileFromProperty != null) {
            files.add(fileFromProperty)
        } else {
            val c = Context()
            val ui = c.getService(UIService::class.java)
            val file = ui.chooseFile(null, FileWidget.OPEN_STYLE)
            files.add(file.absolutePath)
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
        inputHandler?.addKeyBinding("prev_timepoint", "J")

        inputHandler?.addBehaviour("next_timepoint", nextTimePoint)
        inputHandler?.addKeyBinding("next_timepoint", "K")

        inputHandler?.addBehaviour("more_cache", moreCache)
        inputHandler?.addKeyBinding("more_cache", "9")

        inputHandler?.addBehaviour("less_cache", lessCache)
        inputHandler?.addKeyBinding("less_cache", "0")
    }

    @Test override fun main() {
        super.main()
    }
}
