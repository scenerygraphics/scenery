package graphics.scenery.tests.examples.volumes.benchmarks

import bvv.core.shadergen.generate.SegmentTemplate
import bvv.core.shadergen.generate.SegmentType
import graphics.scenery.*
import graphics.scenery.volumes.VolumeManager
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.Volume
import org.joml.Vector3f
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.positionVolumeSlices
import graphics.scenery.volumes.*
import graphics.scenery.volumes.vdi.benchmarks.BenchmarkSetup
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.lwjgl.system.MemoryUtil
import org.zeromq.ZContext

public class VolumeRenderingBenchmark (wWidth: Int = 512, wHeight: Int = 512, val dataset: BenchmarkSetup.Dataset) : SceneryBase("Volume Rendering Benchmark", wWidth, wHeight) {
    val context: ZContext = ZContext(4)

    var cnt = 0

    val cam: Camera = DetachedHeadCamera()

    override fun init() {

        // Step 1: Create renderer, volume and camera
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val benchmarkSetup = BenchmarkSetup(dataset)

        benchmarkSetup.positionCamera(cam)
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            scene.addChild(this)
        }

        val pair = if (benchmarkSetup.is16Bit()) {
            Volume.fromPathRawSplit(Paths.get(System.getenv("SCENERY_BENCHMARK_FILES") + "/" + dataset.toString() + "/" + dataset.toString() + ".raw"), hub = hub, type = UnsignedShortType(), sizeLimit = 2000000000)
        } else {
            Volume.fromPathRawSplit(Paths.get(System.getenv("SCENERY_BENCHMARK_FILES") + "/" + dataset.toString() + "/" + dataset.toString() + ".raw"), hub = hub, type = UnsignedByteType(), sizeLimit = 2000000000)
        }

        val parent = pair.first as RichNode
        val volumeList = pair.second


        val volumeDims = benchmarkSetup.getVolumeDims()
        val pixelToWorld = (0.0075f * 512f) / volumeDims.x

        val volumeManager = VolumeManager(hub,
            useCompute = true,
            customSegments = hashMapOf(
                SegmentType.FragmentShader to SegmentTemplate(
                    this::class.java,

                    "ComputeRaycast.comp",
                    "intersectBoundingBox", "vis", "localNear", "localFar", "SampleVolume", "Convert", "Accumulate"),
            ))
        volumeManager.customTextures.add("OutputRender")

        val outputBuffer = MemoryUtil.memCalloc(windowWidth*windowHeight*4)
        val outputTexture = Texture.fromImage(Image(outputBuffer, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        volumeManager.material().textures["OutputRender"] = outputTexture

        val plane = FullscreenObject()
        scene.addChild(plane)
        plane.material().textures["diffuse"] = volumeManager.material().textures["OutputRender"]!!

        volumeList.forEachIndexed{ i, volume->
            volume.name = "volume_$i"
            benchmarkSetup.setColorMap(volume as BufferedVolume)
            volume.transferFunction = benchmarkSetup.setupTransferFunction()
            volume.pixelToWorldRatio = pixelToWorld
            volume.origin = Origin.FrontBottomLeft
            //step 3: switch the volume's current volume manager to VDI volume manager
            volume.volumeManager = volumeManager
            // Step 4: add the volume to VDI volume manager
            volumeManager.add(volume)
            volume.volumeManager.shaderProperties["doGeneration"] = true
        }

        parent.positionVolumeSlices(volumeList)

        parent.spatial {
            position = Vector3f(0.0f, 0.0f, 0.0f)
        }

        scene.addChild(parent)

        cam.target = Vector3f(volumeDims.x/2*pixelToWorld, -volumeDims.y/2*pixelToWorld, volumeDims.z/2*pixelToWorld)


        // Step 5: add the VDI volume manager to the hub
        hub.add(volumeManager)
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VolumeRenderingBenchmark(1280,720, BenchmarkSetup.Dataset.Kingsnake).main()
        }
    }
}

