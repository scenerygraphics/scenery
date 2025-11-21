package graphics.scenery.tests.examples.basic

import bdv.util.AxisOrder
import bvv.core.VolumeViewerOptions
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Atmosphere
import graphics.scenery.volumes.Colormap
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
 * <Description>
 * A basic example that shows how the atmosphere object can be applied to a scene.
 * Per default, the sun position is synchronized to the local time.
 * The sun position can be changed with Ctrl + arrow keys and fine-tuned with Shift-Ctrl + arrow keys.
 * Call [Atmosphere.attachRotateBehaviours] in the input setup to add sun controls.
 * @author Samuel Pantze
 */
class AtmosphereExample : SceneryBase("Atmosphere Example",
    windowWidth = 1024, windowHeight = 768) {

    /** Whether to run this example in VR mode. */
    private val useVR = false

    lateinit var volume: Volume

    // hand over a fixed direction to not mess up the Argos tests
    private var atmos = Atmosphere(Vector3f(0.2f, 0.6f, -1f))

    private lateinit var hmd: OpenVRHMD

    override fun init() {

        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        renderer?.pushMode = true
        if (useVR) {
            hmd = OpenVRHMD(useCompositor = true)
            hub.add(SceneryElement.HMDInput, hmd)
            renderer?.toggleVR()
        }

        val imp: ImagePlus = IJ.openImage(getDemoFilesPath() + "/volumes/t1-head.zip")
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)

        volume = Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
        volume.colormap = Colormap.get("jet")
        volume.setTransferFunctionRange(10f, 1000f)
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.2f, 1f)
        volume.spatial().scale = Vector3f(10.0f)
        scene.addChild(volume)

        val ambientLight = AmbientLight(0.1f)
        scene.addChild(ambientLight)

        val lights = (1 until 5).map {
            val light = PointLight(10f)
            val spread = 2f
            light.spatial().position = Vector3f(
                Random.randomFromRange(-spread, spread),
                Random.randomFromRange(-spread, spread),
                Random.randomFromRange(-spread, spread),
            )
            light.intensity = 1f
            scene.addChild(light)
            light
        }

        val cam = if (useVR) {DetachedHeadCamera(hmd)} else {DetachedHeadCamera()}
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(70.0f, 768, 512)
            if (!useVR) toggleOrientationOverlay()
            scene.addChild(this)
        }

        scene.addChild(atmos)
    }

    override fun inputSetup() {
        super.inputSetup()

        setupCameraModeSwitching()

        inputHandler?.let { atmos.attachBehaviors(it) }
    }

    companion object {
        /** AtmosphereExample main function */
        @JvmStatic
        fun main(args: Array<String>) {
            AtmosphereExample().main()
        }
    }
}
