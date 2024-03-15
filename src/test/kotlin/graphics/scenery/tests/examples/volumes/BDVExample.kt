package graphics.scenery.tests.examples.volumes

import bdv.spimdata.XmlIoSpimDataMinimal
import bvv.core.VolumeViewerOptions
import org.joml.Vector3f
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.PointLight
import graphics.scenery.SceneryBase
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imagej.lut.LUTService
import net.imagej.ops.OpService
import net.imglib2.histogram.Histogram1d
import org.scijava.Context
import org.scijava.ui.UIService
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.widget.FileWidget
import java.io.File
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
    var ui: UIService? = null
    val ops = context.getService(OpService::class.java)

    override fun init() {
        val files = ArrayList<String>()

        // we can either read a file given via a system property,
        // or we open a dialog for the user to select a file.
        val fileFromProperty = System.getProperty("bdvXML")
        if(fileFromProperty != null) {
            files.add(fileFromProperty)
        } else {
            // If we are running in headless mode, e.g. on CI, we don't
            // try to show any UI, but set file to null.
            var file = if(!settings.get<Boolean>("Headless", false)) {
                ui = context.getService(UIService::class.java)
                ui?.chooseFile(null, FileWidget.OPEN_STYLE)
            } else {
                null
            }

            // If file is null, we'll use one of our example datasets.
            if(file == null) {
                file = File(getDemoFilesPath() + "/volumes/visible-male.xml")
            }
            files.add(file.absolutePath)
        }

        logger.info("Loading BDV XML from ${files.first()}")

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        val options = VolumeViewerOptions().maxCacheSizeInMB(maxCacheSize)
        val v = Volume.fromSpimData(XmlIoSpimDataMinimal().load(files.first()), hub, options)
        v.name = "volume"
        v.colormap = Colormap.get("jet")

        // we set some known properties here for the T1 head example dataset
        if(files.first().endsWith("t1-head.xml")) {
            cam.spatial().position = Vector3f(0.3f, -0.6f, 2.0f)
            v.transferFunction = TransferFunction.ramp(0.01f, 0.8f)
            v.spatial().scale = Vector3f(0.2f)
            v.setTransferFunctionRange(0.0f, 2000.0f)
        }
        v.transferFunction = TransferFunction.ramp(0.2f, 0.15f)
        v.multiResolutionLevelLimits = 0 to 1

        v.viewerState.sources.firstOrNull()?.spimSource?.getSource(0, 0)?.let { rai ->
            var h: Any?
            val duration = measureTimeMillis {
                h = ops.run("image.histogram", rai, 32)
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
            light.spatial().position = Vector3f(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
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
            volume?.goToTimepoint(current + 10)
        }
        val tenTimePointsBack = ClickBehaviour { _, _ ->
            val current = volume?.currentTimepoint ?: 0
            volume?.goToTimepoint(current - 10)
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

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            BDVExample().main()
        }
    }
}
