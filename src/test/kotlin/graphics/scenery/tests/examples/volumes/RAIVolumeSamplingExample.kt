package graphics.scenery.tests.examples.volumes

import bdv.util.AxisOrder
import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.primitives.Cylinder
import graphics.scenery.primitives.Line
import graphics.scenery.utils.MaybeIntersects
import graphics.scenery.utils.extensions.minus
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.img.imageplus.ImagePlusImgFactory
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Vector3f
import tpietzsch.example2.VolumeViewerOptions
import java.text.DecimalFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.roundToInt


class RAIVolumeSamplingExample: SceneryBase("RAIVolume Sampling example" , 1280, 720) {
    lateinit var volume: Volume
    var playing = true

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            scene.addChild(this)
        }

        val shell = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material {
            cullingMode = Material.CullingMode.None
            diffuse = Vector3f(0.2f, 0.2f, 0.2f)
            specular = Vector3f(0.0f)
            ambient = Vector3f(0.0f)
        }
        scene.addChild(shell)

        val p1 = Icosphere(0.1f, 2)
        p1.spatial().position = Vector3f(0.0f, 0.5f, -4.0f)
        p1.material().diffuse = Vector3f(0.3f, 0.3f, 0.8f)
        scene.addChild(p1)

        val p2 = Icosphere(0.1f, 2)
        p2.spatial().position = Vector3f(0.0f,0.5f,2.0f)//Vector3f(0.0f, 0.5f, 2.0f)
        p2.material().diffuse = Vector3f(0.3f, 0.8f, 0.3f)

        scene.addChild(p2)

        val connector = Cylinder.betweenPoints(p1.spatial().position, p2.spatial().position)
        connector.material().diffuse = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(connector)

        p1.update.add {
            connector.spatial().orientBetweenPoints(p1.spatial().position, p2.spatial().position, true, true)
        }

        p2.update.add {
            connector.spatial().orientBetweenPoints(p1.spatial().position, p2.spatial().position, true, true)
        }


        val file = "C://Users//lanru//Desktop//Pdu_H2BeGFP_CAAXmCherry.xml"

//        val imp: ImagePlus = IJ.openImage("https://imagej.nih.gov/ij/images/t1-head.zip")

        val img = ImagePlusImgFactory(UnsignedByteType ()).create(256, 256, 25)
        val r= img.randomAccess()
        val random = Random()
        for (i in 0..250)
        {
            for(j in 0..250)
            {
                for(z in 0..20) {
                    r.setPosition(i, 0)
                    r.setPosition(j, 1)
                    r.setPosition(z, 2)
                    val t: UnsignedByteType = r.get()
                    t.set(25)
                }
            }
        }

//        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)
        volume = Volume.fromRAI(img, UnsignedByteType (), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
        volume = Volume.fromXML("E:\\dataset\\Pdu_H2BeGFP_CAAXmCherry.xml",hub,VolumeViewerOptions())
//        volume = Volume.fromXML(file,hub, VolumeViewerOptions())
//        volume.spatial {
//            position = Vector3f(0.0f, 5.0f, 0.0f)
//            scale = Vector3f(7.5f, 7.5f, 7.5f)
//        }
//        volume.transferFunction = TransferFunction.ramp(0.001f, 1.0f, 0.004f)
//        volume.colormap = Colormap.get("jet")

        volume.spatial {
            position = Vector3f(0.0f, 0.0f, 0.0f)
            scale = Vector3f(0.5f, 0.5f, 0.5f)
        }
        volume.transferFunction = TransferFunction.ramp(0.1f, 1.0f, 0.004f)
        scene.addChild(volume)


        Light.createLightTetrahedron<PointLight>(spread = 4.0f, radius = 15.0f, intensity = 0.5f)
            .forEach { scene.addChild(it) }

        val origin = Box(Vector3f(0.1f, 0.1f, 0.1f))
        origin.material().diffuse = Vector3f(0.8f, 0.0f, 0.0f)
        scene.addChild(origin)

        scene.export("rai.scenery")

        val p3 = Icosphere(0.02f, 2)
        p3.material().diffuse = Vector3f(0.3f, 0.3f, 0.8f)
        scene.addChild(p3)

        val p4 = Icosphere(0.02f, 2)
        p4.material().diffuse = Vector3f(0.3f, 0.8f, 0.3f)
        scene.addChild(p4)



        thread {
            while (!scene.initialized) {
                Thread.sleep(200)
            }
            while(running) {
                if (playing ) {
                    volume.previousTimepoint()
                }
                val intersection = volume.spatial()
                    .intersectAABB(p1.spatial().position, (p2.spatial().position - p1.spatial().position).normalize())
                if (intersection is MaybeIntersects.Intersection) {
                    // println("there is intersection!")
                    val scale = volume.localScale()
                    val localEntry = (intersection.relativeEntry)// + Vector3f(1.0f)) * (1.0f/2.0f)
                    val localExit = (intersection.relativeExit)// + Vector3f(1.0f)) * (1.0f/2.0f)
                    p3.spatial().position = intersection.entry
                    p4.spatial().position = intersection.exit
                    val nf = DecimalFormat("0.0000")
                    logger.info(
                        "Ray intersects volume at world=${intersection.entry.toString(nf)}/${
                            intersection.exit.toString(
                                nf
                            )
                        } local=${localEntry.toString(nf)}/${localExit.toString(nf)} localScale=${scale.toString(nf)}"
                    )

                    val (samples, _) = volume.sampleRay(localEntry, localExit) ?: null to null
                    logger.info("Samples: ${samples?.joinToString(",") ?: "(no samples returned)"}")

                    if (samples == null) {
                        continue
                    }

                    val diagram = if (connector.getChildrenByName("diagram").isNotEmpty()) {
                        connector.getChildrenByName("diagram").first() as Line
                    } else {
                        val l = Line(capacity = (volume.getDimensions().length() * 2).roundToInt())
                        connector.addChild(l)
                        l
                    }
                }
                Thread.sleep(20000)
            }

        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
//        val toggleRenderingMode = object : ClickBehaviour {
//            var modes = Volume.RenderingMethod.values()
//            var currentMode = (scene.find("volume") as? Volume)!!.renderingMethod
//
//            override fun click(x: Int, y: Int) {
//                currentMode = modes.getOrElse(modes.indexOf(currentMode) + 1 % modes.size) { Volume.RenderingMethod.AlphaBlending }
//
//                (scene.find("volume") as? Volume)?.renderingMethod = currentMode
//                logger.info("Switched volume rendering mode to $currentMode")
//            }
//        }
//
//        val togglePlaying = ClickBehaviour { _, _ ->
//            playing = !playing
//        }
//
//        inputHandler?.addBehaviour("toggle_rendering_mode", toggleRenderingMode)
//        inputHandler?.addKeyBinding("toggle_rendering_mode", "M")
//
//        inputHandler?.addBehaviour("toggle_playing", togglePlaying)
//        inputHandler?.addKeyBinding("toggle_playing", "G")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            RAIVolumeSamplingExample().main()
        }
    }

}
