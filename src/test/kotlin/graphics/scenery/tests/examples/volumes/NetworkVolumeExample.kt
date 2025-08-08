package graphics.scenery.tests.examples.volumes

import bdv.util.AxisOrder
import bvv.core.VolumeViewerOptions
import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.tests.examples.network.SlimClient
import graphics.scenery.utils.extensions.timesAssign
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Vector3f
import kotlin.concurrent.thread

/**
 * Syncing volume parameters and scene.
 *
 * Start master with vm param:
 * -Dscenery.Server=true
 *
 * For client see [SlimClient]
 */
class NetworkVolumeExample : SceneryBase("SpimData Rendering example", 1280, 720, false) {
    lateinit var volume: Volume

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))


        //"C:\\Users\\JanCasus\\volumes\\HisYFP-SPIM.xml"
        //"C:\\Users\\JanCasus\\volumes\\ct-scan.tif"

        /**
         * Following are examples of [Volume.VolumeInitializer]. The path need to adjusted.
         */
        val drosophila = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given("C:\\Users\\JanCasus\\volumes\\drosophila.xml"),
            Volume.VolumeFileSource.VolumeType.SPIM
        )

        val resource = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Resource("/graphics/scenery/tests/unit/volume/t1-head.zip"),
            Volume.VolumeFileSource.VolumeType.TIFF
        )

        // add the following, adjusted to you path to VM Options of both client and Server :
        // -Dscenery.VolumeFile="C:\\Users\\JanCasus\\volumes\\t1-head.tif"
        val console = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Settings(),
            Volume.VolumeFileSource.VolumeType.TIFF
        )

        val online = IJVolumeInitializer(getDemoFilesPath() + "/volumes/t1-head.zip")


        val choice = online

        volume = Volume.forNetwork(choice, hub)
        scene.addChild(volume)
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        volume.spatial {
            position.y += 3f
            scale *= 3f
            needsUpdate = true
        }

        thread {
            while (false) {
                Thread.sleep(5000)
                volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
                println("updating volume data done")
            }
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            wantsSync = true
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

        Light.createLightTetrahedron<PointLight>(spread = 4.0f, radius = 15.0f, intensity = 0.5f)
            .forEach { scene.addChild(it) }

        val origin = Box(Vector3f(0.1f, 0.1f, 0.1f))
        origin.material().diffuse = Vector3f(0.8f, 0.0f, 0.0f)
        scene.addChild(origin)
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            NetworkVolumeExample().main()
        }
    }
}

class IJVolumeInitializer(private val path: String) : Volume.VolumeInitializer {

    override fun initializeVolume(hub: Hub): Volume {

        val imp: ImagePlus = IJ.openImage(path)
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)

        return Volume.fromRAI(
            img,
            UnsignedShortType(),
            AxisOrder.DEFAULT,
            "Volume loaded with IJ",
            hub,
            VolumeViewerOptions()
        )
    }
}
