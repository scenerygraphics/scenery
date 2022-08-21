package graphics.scenery.tests.examples.volumes

import bdv.util.AxisOrder
import graphics.scenery.*
import org.joml.Vector3f
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material
import graphics.scenery.utils.extensions.timesAssign
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import tpietzsch.example2.VolumeViewerOptions
import kotlin.concurrent.thread

/**

 */
class ReplacingAndLateLoadingVolumesTestExample: SceneryBase("RAI Rendering example", 1280, 720) {
    var volume: Volume? = null

    var count = 0

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

        val imp: ImagePlus = IJ.openImage("https://imagej.nih.gov/ij/images/t1-head.zip")
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)

        /*volume = Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        scene.addChild(volume)
*/
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

        thread {
            Thread.sleep(10000)
            while(true){
                Thread.sleep(2000)
                println("replacing Volume")
                volume?.let { scene.removeChild(it) }
                val tmpV = Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
                tmpV.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
                tmpV.spatial().position = Vector3f(2f * count - 2,0f,0f)
                tmpV.spatial().scale *= 0.3f
                scene.addChild(tmpV)
                volume = tmpV
                count = (count + 1) % 3
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ReplacingAndLateLoadingVolumesTestExample().main()
        }
    }
}
