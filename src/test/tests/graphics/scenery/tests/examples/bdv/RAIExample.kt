package graphics.scenery.tests.examples.bdv

import bdv.util.AxisOrder
import cleargl.GLVector
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.PointLight
import graphics.scenery.SceneryBase
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.bdv.Volume
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.junit.Test
import tpietzsch.example2.VolumeViewerOptions

/**
 * BDV Rendering Example loading a RAII
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Tobias Pietzsch <pietzsch@mpi-cbg.de>
 */
class RAIExample: SceneryBase("RAII Rendering example", 1280, 720) {
    lateinit var volume: Volume

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight)
            active = true

            position = GLVector(0.0f, 0.0f, 5.0f)
            scene.addChild(this)
        }

        val imp: ImagePlus = IJ.openImage("https://imagej.nih.gov/ij/images/t1-head.zip")
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)

        volume = Volume.fromRAII(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f)
        scene.addChild(volume)

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

    @Test override fun main() {
        super.main()
    }
}
