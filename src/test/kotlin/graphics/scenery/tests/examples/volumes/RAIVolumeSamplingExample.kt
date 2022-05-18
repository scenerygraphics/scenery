package graphics.scenery.tests.examples.volumes

import bdv.util.AxisOrder
import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Cylinder
import graphics.scenery.primitives.Line
import graphics.scenery.utils.MaybeIntersects
import graphics.scenery.utils.RingBuffer
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import org.scijava.Context
import org.scijava.ui.UIService
import org.scijava.widget.FileWidget
import tpietzsch.example2.VolumeViewerOptions
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.text.DecimalFormat
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class RAIVolumeSamplingExample: SceneryBase("RAIVolume Sampling example" , 1280, 720) {
    lateinit var volume: Volume

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
        p1.spatial().position = Vector3f(0.0f, 0.5f, -2.0f)
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

        val imp: ImagePlus = IJ.openImage("https://imagej.nih.gov/ij/images/t1-head.zip")
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)
        volume = Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
        volume.spatial {
            position = Vector3f(0.0f, 0.0f, 0.0f)
            scale = Vector3f(1.0f, 1.0f, 1.0f)
        }
        volume.colormap = Colormap.get("viridis")

//        val files = ArrayList<String>()
//        files.add("E:\\dataset\\test")
//        val folder = File(files.first())
//        volume = Volume.fromPath(folder.toPath(),hub)
//        volume.spatial()
//        {
//            //y: + up
//            position = Vector3f(-1.0f, 5.0f, 0.0f)
//            scale = Vector3f(10.0f, 10.0f,30.0f)
//        }
//        volume.colormap = Colormap.get("jet")


        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        scene.addChild(volume)


        Light.createLightTetrahedron<PointLight>(spread = 4.0f, radius = 15.0f, intensity = 0.5f)
            .forEach { scene.addChild(it) }

        val origin = Box(Vector3f(0.1f, 0.1f, 0.1f))
        origin.material().diffuse = Vector3f(0.8f, 0.0f, 0.0f)
        scene.addChild(origin)

        scene.export("rai.scenery")

        val p3 = Icosphere(0.2f, 2)
        p3.material().diffuse = Vector3f(0.3f, 0.3f, 0.8f)
        scene.addChild(p3)

        val p4 = Icosphere(0.2f, 2)
        p4.material().diffuse = Vector3f(0.3f, 0.8f, 0.3f)
        scene.addChild(p4)



        thread {
            while (!scene.initialized) {
                Thread.sleep(200)
            }
            while(running) {
                val intersection = volume.spatial()
                    .intersectAABB(p1.spatial().position, (p2.spatial().position - p1.spatial().position).normalize())
                if (intersection is MaybeIntersects.Intersection) {
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
            }

        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            RAIVolumeSamplingExample().main()
        }
    }

}
