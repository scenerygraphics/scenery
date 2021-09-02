package graphics.scenery.tests.examples.volumes

import bdv.spimdata.XmlIoSpimDataMinimal
import graphics.scenery.*
import org.joml.Vector3f
import graphics.scenery.backends.Renderer
import graphics.scenery.primitives.TextBoard
import graphics.scenery.utils.extensions.plus
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imagej.ops.OpService
import net.imglib2.histogram.Histogram1d
import net.imglib2.type.numeric.ARGBType
import org.joml.Vector4f
import org.scijava.Context
import org.scijava.ui.UIService
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.widget.FileWidget
import tpietzsch.example2.VolumeViewerOptions
import java.nio.file.Paths
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/**
 * BDV Rendering Example
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class CASUSOpenHouseDrosophila: SceneryBase("Behold, the Open House Drosophila!", 1920 , 1200) {
    lateinit var volume: Volume

    override fun init() {
        val files = arrayListOf("D:/datasets/Tobi/111010_weber_full.xml")

        logger.info("Loading BDV XML from ${files.first()}")

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            spatial().position = Vector3f(2.5f, -0.5f, 7.0f)
            scene.addChild(this)
        }

        val v = Volume.fromPathRaw(Paths.get("C:/Users/ulrik/Datasets/droso-royer-autopilot-transposed"), hub)

        v.name = "volume"
        v.colormap = Colormap.get("hot") // jet, hot, rainbow, plasma, grays
        v.spatial {
            position = Vector3f(1.0f, 0.0f, 1.0f)
            scale = Vector3f(5.0f, 25.0f, 5.0f)
        }
        v.ds.converterSetups[0].setDisplayRange(20.0, 500.0)
        v.transferFunction = TransferFunction.ramp(0.2f, 0.3f)
        v.spatial().rotation.rotateZ(0.3f)
        v.origin = Origin.Center
        scene.addChild(v)

        volume = v

        val deCaption = TextBoard()
        deCaption.spatial().position = Vector3f(1.0f, -2.0f, 2.0f)
        deCaption.fontFamily = "SourceSansPro-Light.ttf"
        deCaption.transparent = 0
        deCaption.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
        deCaption.backgroundColor = Vector4f(10.0f, 10.0f, 10.0f, 1.0f)
        deCaption.spatial().scale = Vector3f(0.25f)
        scene.addChild(deCaption)

        val enCaption = TextBoard()
        enCaption.spatial().position = Vector3f(1.1f, -2.6f, 2.0f)
        enCaption.fontFamily = "SourceSansPro-Light.ttf"
        enCaption.transparent = 0
        enCaption.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
        enCaption.backgroundColor = Vector4f(10.0f, 10.0f, 10.0f, 1.0f)
        enCaption.spatial().scale = Vector3f(0.25f)
        scene.addChild(enCaption)


        val plCaption = TextBoard()
        plCaption.spatial().position = Vector3f(0.9f, -2.3f, 2.0f)
        plCaption.fontFamily = "SourceSansPro-Light.ttf"
        plCaption.transparent = 0
        plCaption.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
        plCaption.backgroundColor = Vector4f(10.0f, 10.0f, 10.0f, 1.0f)
        plCaption.spatial().scale = Vector3f(0.25f)
        scene.addChild(plCaption)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.spatial().position = Vector3f(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            light.intensity = 50.0f
            scene.addChild(light)
        }

        thread {
            while(running) {
                when (v.currentTimepoint) {
                    in 0..50 -> {
                        deCaption.text = " Das ist eine Fruchtfliege waehrend ihrer fruehen Entwicklung. "
                        enCaption.text = " This is a fruit fly during early development. "
                        plCaption.text = " Jest to muszka owocowa na wczesnym etapie rozwoju. "
                    }
                    in 51..100 -> {
                        deCaption.text = " Bei der Gastrulation falten sich Zellen von der Aussenwand nach innen. "
                        enCaption.text = " During gastrulation, cells move from the outside to the inside. "
                        plCaption.text = " We need vocals here. "
                    }
                    in 101..260 -> {
                        deCaption.text = " Interessante Sachen passieren. "
                        enCaption.text = " Interesting stuff happens. "
                        plCaption.text = " More beers. "
                    }
                }

                Thread.sleep(150)
            }

        }

        thread {
            while(running) {
                if(ticks % 100L == 0L) {
                    drift1 *= -1.0f
                }
                if(ticks % 80L == 0L) {
                    drift2 *= -1.0f
                }
                if(ticks % 95L == 0L) {
                    drift3 *= -1.0f
                }

                deCaption.spatial().position = deCaption.spatial().position + Vector3f(drift1, 0.0f, 0.0f)
                enCaption.spatial().position = enCaption.spatial().position + Vector3f(drift2, 0.0f, 0.0f)
                plCaption.spatial().position = plCaption.spatial().position + Vector3f(drift2, 0.0f, 0.0f)

                Thread.sleep(10)
            }
        }

        thread {
            while(renderer?.firstImageReady == false) {
                Thread.sleep(200)
            }

            while(running) {
                if(animating) {
                    v.spatial().rotation = v.spatial().rotation.rotateLocalX(0.001f)
                }

                Thread.sleep(10)
            }
        }

        thread {
            while(renderer?.firstImageReady == false) {
                Thread.sleep(200)
            }

            while(running) {
                if(animating) {
                    if(v.currentTimepoint == v.timepointCount - 1) {
                        v.goToTimepoint(0)
                    } else {
                        v.nextTimepoint()
                    }
                }

                Thread.sleep(333)
            }
        }
    }


    var drift1 = 0.0001f
    var drift2 = -0.00015f
    var drift3 = 0.00012f
    var animating = true

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
            CASUSOpenHouseDrosophila().main()
        }
    }
}
