package graphics.scenery.tests.examples.bdv

import bdv.spimdata.XmlIoSpimDataMinimal
import cleargl.GLVector
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.PointLight
import graphics.scenery.SceneryBase
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.bdv.Volume
import net.imagej.notebook.chart.Histogram1dToHTMLConverter
import net.imagej.ops.OpService
import net.imglib2.histogram.Histogram1d
import org.junit.Test
import org.scijava.Context
import org.scijava.ui.UIService
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.widget.FileWidget
import tpietzsch.example2.VolumeViewerOptions
import java.util.*
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/**
 * BDV Rendering Example
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BDVExample: SceneryBase("BDV Rendering example", 1280, 720) {
    var volume: Volume? = null
    var maxCacheSize = 512
    val context = Context(UIService::class.java, OpService::class.java)
    val ui = context.getService(UIService::class.java)
    val ops = context.getService(OpService::class.java)

    override fun init() {
        val files = ArrayList<String>()

        val fileFromProperty = System.getProperty("bdvXML")
        if(fileFromProperty != null) {
            files.add(fileFromProperty)
        } else {
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
        v.viewerState.sources.firstOrNull()?.spimSource?.getSource(0, 0)?.let { rai ->
            var h: Any? = null
            val duration = measureTimeMillis {
                h = ops.run("image.histogram", rai, 1024)
            }

            val histogram = h as? Histogram1d<*> ?: return@let
            logger.info("Got histogram $histogram for t=0 l=0 in $duration ms")

            logger.info("min: ${histogram.min()} max: ${histogram.max()} bins: ${histogram.binCount} DFD: ${histogram.dfd().modePositions().firstOrNull()?.joinToString(",")}")
            histogram.forEachIndexed { index, longType ->
                val relativeCount = (longType.get().toFloat()/histogram.totalCount().toFloat()) * histogram.binCount
                val bar = "*".repeat(relativeCount.roundToInt())
                val position = (index.toFloat()/histogram.binCount.toFloat())*(65536.0f/histogram.binCount.toFloat())
               logger.info("%.3f: $bar".format(position))
            }
        }
        scene.addChild(v)

        volume = v

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 50.0f
            light.position.length2()
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
