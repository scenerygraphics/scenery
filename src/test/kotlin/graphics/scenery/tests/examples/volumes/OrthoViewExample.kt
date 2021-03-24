package graphics.scenery.tests.examples.volumes

import bdv.util.AxisOrder
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.SlicingPlane
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import tpietzsch.example2.VolumeViewerOptions
import kotlin.concurrent.thread
import graphics.scenery.numerics.Random

/**
 * Volume Ortho View Example using the "BDV Rendering Example loading a RAII"
 *
 * @author  Jan Tiemann <j.tiemann@hzdr.de>
 */
class OrthoViewExample : SceneryBase("Ortho View example", 1280, 720) {
    lateinit var volume: Volume


    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            position = Vector3f(0.0f, 0.0f, 5.0f)
            scene.addChild(this)
        }


        val shell = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material.cullingMode = Material.CullingMode.None
        shell.material.diffuse = Vector3f(0.2f, 0.2f, 0.2f)
        shell.material.specular = Vector3f(0.0f)
        shell.material.ambient = Vector3f(0.0f)
        scene.addChild(shell)

        Light.createLightTetrahedron<PointLight>(spread = 4.0f, radius = 15.0f, intensity = 0.5f)
            .forEach { scene.addChild(it) }

        val origin = Box(Vector3f(0.1f, 0.1f, 0.1f))
        origin.material.diffuse = Vector3f(0.8f, 0.0f, 0.0f)
        scene.addChild(origin)

        val imp: ImagePlus = IJ.openImage("https://imagej.nih.gov/ij/images/t1-head.zip")
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)

        volume = Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        scene.addChild(volume)
        volume.scale = Vector3f(1f,1f,1.5f)

        val sliceXY = SlicingPlane()
        val sliceXZ = SlicingPlane()
        sliceXZ.rotation = sliceXZ.rotation.rotateX((Math.PI/2).toFloat())
        val sliceYZ = SlicingPlane()
        sliceYZ.rotation = sliceYZ.rotation.rotateZ((Math.PI/2).toFloat())

        scene.addChild(sliceXY)
        scene.addChild(sliceXZ)
        scene.addChild(sliceYZ)

        sliceXY.addTargetVolume(volume)
        sliceXZ.addTargetVolume(volume)
        sliceYZ.addTargetVolume(volume)

    }

    override fun inputSetup() {
        setupCameraModeSwitching()

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            OrthoViewExample().main()
        }
    }
}
