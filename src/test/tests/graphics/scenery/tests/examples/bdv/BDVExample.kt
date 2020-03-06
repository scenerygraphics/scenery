package graphics.scenery.tests.examples.bdv

import bdv.spimdata.XmlIoSpimDataMinimal
import cleargl.GLVector
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.PointLight
import graphics.scenery.SceneryBase
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.bdv.Volume
import org.junit.Test
import org.scijava.Context
import org.scijava.ui.UIService
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.widget.FileWidget
import tpietzsch.example2.VolumeViewerOptions
import java.util.*

/**
 * BDV Rendering Example
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BDVExample: SceneryBase("BDV Rendering example", 1280, 720) {
    var volume: Volume? = null
    var maxCacheSize = 512

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

            scene.addChild(this)
        }

        val options = VolumeViewerOptions().maxCacheSizeInMB(maxCacheSize)
        val v = Volume.fromSpimData(XmlIoSpimDataMinimal().load(files.first()), hub, options)
        v.name = "volume"
//        v.scale = GLVector(0.02f, 0.02f, 0.02f)
        v.updateWorld(true, true)
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

        inputHandler?.addBehaviour("prev_timepoint", prevTimePoint)
        inputHandler?.addKeyBinding("prev_timepoint", "H")

        inputHandler?.addBehaviour("10_prev_timepoint", tenTimePointsBack)
        inputHandler?.addKeyBinding("10_prev_timepoint", "shift H")

        inputHandler?.addBehaviour("next_timepoint", nextTimePoint)
        inputHandler?.addKeyBinding("next_timepoint", "L")

        inputHandler?.addBehaviour("10_next_timepoint", tenTimePointsForward)
        inputHandler?.addKeyBinding("10_next_timepoint", "shift L")
    }

    @Test override fun main() {
        super.main()
    }
}
