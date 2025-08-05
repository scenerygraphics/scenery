package graphics.scenery.tests.examples.compute

import bdv.util.AxisOrder
import bvv.core.VolumeViewerOptions
import bvv.core.shadergen.generate.SegmentTemplate
import bvv.core.shadergen.generate.SegmentType
import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.TransferFunctionEditor
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.VolumeManager
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.lwjgl.system.MemoryUtil
import java.io.File

/**
 * Example showing using a custom [graphics.scenery.volumes.VolumeManager] with
 * compute shaders instead of the regular vertex/fragment shader combo.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class CustomVolumeManagerExample : SceneryBase("CustomVolumeManagerExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 1280, 720))

        val volumeManager = VolumeManager(hub,
            useCompute = true,
            customSegments = hashMapOf(
                SegmentType.FragmentShader to SegmentTemplate(
                    this.javaClass,
                    "ComputeVolume.comp",
                    "intersectBoundingBox", "vis", "localNear", "localFar", "SampleVolume", "Convert", "Accumulate"),
            ))
        volumeManager.customTextures.add("OutputRender")

        val outputBuffer = MemoryUtil.memCalloc(1280*720*4)
        val outputTexture = Texture.fromImage(Image(outputBuffer, 1280, 720), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        volumeManager.material().textures["OutputRender"] = outputTexture

        hub.add(volumeManager)

        val imp: ImagePlus = IJ.openImage(getDemoFilesPath() + "/volumes/t1-head.zip")
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)

        val volume = Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())

        //transfer function and display range chosen empirically
        volume.minDisplayRange = 0f
        volume.maxDisplayRange = 1128.0f
        volume.transferFunction.addControlPoint(0.0f, 0.0f)
        volume.transferFunction.addControlPoint(0.12f, 0.3f)
        volume.transferFunction.addControlPoint(0.30f, 0.0f)
        volume.transferFunction.addControlPoint(1.0f, 0.0f)
        volume.spatial().scale = Vector3f(10.0f)

        scene.addChild(volume)

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        box.material {
            textures["diffuse"] = outputTexture
            metallic = 0.0f
            roughness = 1.0f
        }

        scene.addChild(box)

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CustomVolumeManagerExample().main()
        }
    }
}
