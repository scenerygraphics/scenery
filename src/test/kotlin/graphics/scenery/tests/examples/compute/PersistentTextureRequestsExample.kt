package graphics.scenery.tests.examples.compute

import bdv.util.AxisOrder
import bvv.core.VolumeViewerOptions
import bvv.core.shadergen.generate.SegmentTemplate
import bvv.core.shadergen.generate.SegmentType
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.VolumeManager
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Example to show how persistent texture requests - that are served once per frame - may be created
 * and accessed. These may be particularly be useful, e.g, when compute shaders are used and output
 * textures need to be accessed every time they are updated.
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 * @author Ulrik Günther <hello@ulrik.is>
 */
class PersistentTextureRequestsExample : SceneryBase("PersistentTextureRequestsExample") {

    private val counter = AtomicInteger(0)
    private var totalFrames = -1L

    override fun init() {
        renderer = hub.add(
            SceneryElement.Renderer,
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
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        volume.spatial().scale = Vector3f(20.0f)
        scene.addChild(volume)

        val box = Box(Vector3f(2.0f))
        box.name = "le box du win"
        box.material {
            textures["diffuse"] = outputTexture
            metallic = 0.5f
            roughness = 1.0f
        }

        scene.addChild(box)

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 15.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial().position = Vector3f(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }

        renderer?.runAfterRendering?.add {
            // persistent texture requests run in sync with frame rendering,
            // so their count should always be one less than the total number
            // of frames rendered (totalFrames is only incremented at the very
            // end of the render loop, in submitFrame).
            renderer?.let { assertEquals(counter.get().toLong(), it.totalFrames+1) }
        }

        thread {
            val opTexture = volumeManager.material().textures["OutputRender"]!!

            var prevCounter = 0

            (renderer as VulkanRenderer)?.persistentTextureRequests?.add(opTexture to counter)

            // this is the loop where you may do your tasks that are asynchronous to rendering
            while (renderer?.shouldClose == false) {
                while (counter.get() == prevCounter) {
                    Thread.sleep(5)
                }
                prevCounter = counter.get()

                //updated texture has been returned
                val buffer = opTexture.contents
                //the buffer can now, e.g., be transmitted, as is required for parallel rendering

                if (buffer != null && prevCounter == 100) {
                    val file = SystemHelpers.dumpToFile(buffer, "texture-${SystemHelpers.formatDateTime(delimiter = "_")}.raw")
                    assertNotNull(file, "File handle should not be null.")

                    val sum = file.readBytes().sum()
                    logger.info("Dumped: $file, ${file.length()} bytes, sum=$sum")
                    assertEquals(file.length(), 1280*720*4, "File should contain the correct number of bytes for a 1280x720xRGBA image")
                    assert(sum > 3000000) { "Sum of bytes in file should be non-zero" }
                }
            }
        }
    }

    /**
     * Companion object for providing a main method.
     */
    companion object {
        /**
         * The main entry point. Executes this example application when it is called.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            PersistentTextureRequestsExample().main()
        }
    }
}
