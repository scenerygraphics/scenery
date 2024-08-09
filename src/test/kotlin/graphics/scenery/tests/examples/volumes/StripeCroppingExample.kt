package graphics.scenery.tests.examples.volumes

import bdv.util.AxisOrder
import bvv.core.VolumeViewerOptions
import graphics.scenery.*
import org.joml.Vector3f
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material
import graphics.scenery.volumes.SlicingPlane
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.TransferFunctionEditor
import graphics.scenery.volumes.Volume
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Quaternionf
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Using two slicing planes to stripe crop a volume. + some animation
 *
 * [CroppingStripe] is the interesting part of this file.
 *
 * Based on RAIExample.kt
 */
class StripeCroppingExample: SceneryBase("RAI Rendering example", 1280, 720) {
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

        val imp: ImagePlus = IJ.openImage(getDemoFilesPath() + "/volumes/t1-head.zip")
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)

        volume = Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
        volume.spatial().scale = Vector3f(20.0f)
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.8f, 0.2f)
        volume.maxDisplayRange = 2000f
        scene.addChild(volume)

        val shell = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        shell.material {
            cullingMode = Material.CullingMode.None
            diffuse = Vector3f(0.2f, 0.2f, 0.2f)
            specular = Vector3f(0.0f)
            ambient = Vector3f(0.0f)
        }
        scene.addChild(shell)

        Light.createLightTetrahedron<PointLight>(spread = 4.0f, radius = 15.0f, intensity = 0.7f)
            .forEach { scene.addChild(it) }

        val croppingStripe = CroppingStripe(volume, 1f)
        scene.addChild(croppingStripe)

        //some fun animation
        val animator = CroppingExample.Animator(croppingStripe.spatial())
        thread {
            while(true){
                Thread.sleep(20)
                animator.animate()
                croppingStripe.thickness = 0.2f + abs(((System.currentTimeMillis() % 10000) / 5000f)-1) * 0.8f
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            StripeCroppingExample().main()
        }
    }
}

class CroppingStripe(val target: Volume, thickness: Float): RichNode("Cropping Stripe"){
    var thickness = thickness
        set(value) {
            field = value
            updateThickness()
        }

    val plane1 = SlicingPlane()
    val plane2 = SlicingPlane()

    init {
        plane1.addTargetVolume(target)
        this.addChild(plane1)

        plane2.addTargetVolume(target)
        plane2.spatial().rotation = Quaternionf().lookAlong(Vector3f(0f,0f,1f),Vector3f(0f,-1f,0f))
        this.addChild(plane2)

        target.slicingMode = Volume.SlicingMode.Cropping

        updateThickness()
    }

    private fun updateThickness() {
        plane1.spatial().position = Vector3f(0f,thickness/2,0f)
        plane2.spatial().position = Vector3f(0f,thickness/-2,0f)
    }
}
