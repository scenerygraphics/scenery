package graphics.scenery.tests.examples.volumes.benchmarks

import graphics.scenery.*
import graphics.scenery.volumes.VolumeManager
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.vdi.*
import org.joml.Vector3f
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.volumes.*
import graphics.scenery.volumes.vdi.benchmarks.BenchmarkSetup
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.*
import org.zeromq.ZContext
import java.io.*
import java.nio.ByteBuffer
import kotlin.concurrent.thread

public class VDIGenerationBenchmark (wWidth: Int = 512, wHeight: Int = 512, val maxSupersegments: Int = 20, val dataset: BenchmarkSetup.Dataset, val storeVDI: Boolean) : SceneryBase("Volume Generation Example", wWidth, wHeight) {
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


        val type = if(benchmarkSetup.is16Bit()) {
            UnsignedShortType()
        }else {
            UnsignedByteType()
        }

        val pair = Volume.fromPathRawSplit(Paths.get(System.getenv("SCENERY_BENCHMARK_FILES") + "\\" + dataset.toString() + "\\" + dataset.toString() + ".raw"), hub = hub, type = UnsignedShortType(), sizeLimit = 1500000000)
        val parent = pair.first as RichNode
        val volumeList = pair.second


        val volumeDims = benchmarkSetup.getVolumeDims()
        val pixelToWorld = (0.0075f * 512f) / volumeDims.x


        // Step 2: Create VDI Volume Manager
        val vdiVolumeManager = VDIVolumeManager( hub, windowWidth, windowHeight, maxSupersegments, scene).createVDIVolumeManger()

        volumeList.forEachIndexed{ i, volume->
            volume.name = "volume_$i"
            benchmarkSetup.setColorMap(volume as BufferedVolume)
            volume.transferFunction = benchmarkSetup.setupTransferFunction()
            volume.pixelToWorldRatio = pixelToWorld
            volume.origin = Origin.FrontBottomLeft
            //step 3: switch the volume's current volume manager to VDI volume manager
            volume.volumeManager = vdiVolumeManager
            // Step 4: add the volume to VDI volume manager
            vdiVolumeManager.add(volume)
            volume.volumeManager.shaderProperties["doGeneration"] = true
        }

        Volume.positionSlices(volumeList, volumeList.first().pixelToWorldRatio)

        parent.spatial {
            position = Vector3f(0.0f, 0.0f, 0.0f)
        }

        scene.addChild(parent)

        cam.target = Vector3f(volumeDims.x/2*pixelToWorld, -volumeDims.y/2*pixelToWorld, volumeDims.z/2*pixelToWorld)


        // Step 5: add the VDI volume manager to the hub
        hub.add(vdiVolumeManager)

        // Step 6: Store VDI Generated
        val volumeDimensions3i = Vector3f(volumeDims.x, volumeDims.y,volumeDims.z)
        val model = volumeList.first().spatial().world

        val vdiData = VDIData(
            VDIBufferSizes(),
            VDIMetadata(
                index = cnt,
                projection = cam.spatial().projection,
                view = cam.spatial().getTransformation(),
                volumeDimensions = volumeDimensions3i,
                model = model,
                nw = volumeList.first().volumeManager.shaderProperties["nw"] as Float,
                windowDimensions = Vector2i(cam.width, cam.height)
            )
        )
        if (storeVDI) {
            thread {
                storeVDI(vdiVolumeManager, vdiData)
            }
        }
    }

    private fun storeVDI(vdiVolumeManager: VolumeManager, vdiData: VDIData) {
        data class Timer(var start: Long, var end: Long)
        val tGeneration = Timer(0, 0)

        var vdiDepthBuffer: ByteBuffer?
            var vdiColorBuffer: ByteBuffer?
            var gridCellsBuff: ByteBuffer?
            var iterationBuffer: ByteBuffer?

            val volumeList = ArrayList<BufferedVolume>()
        volumeList.add(vdiVolumeManager.nodes.first() as BufferedVolume)
        val VDIsGenerated = AtomicInteger(0)
        while (renderer?.firstImageReady == false) {
            Thread.sleep(50)
        }

        val vdiColor = vdiVolumeManager.material().textures["OutputSubVDIColor"]!!
            val colorCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiColor to colorCnt)

        val vdiDepth = vdiVolumeManager.material().textures["OutputSubVDIDepth"]!!
            val depthCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiDepth to depthCnt)


        val gridCells = vdiVolumeManager.material().textures["OctreeCells"]!!
            val gridTexturesCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(gridCells to gridTexturesCnt)

        val vdiIteration = vdiVolumeManager.material().textures["Iterations"]!!
            val iterCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiIteration to iterCnt)

        var prevColor = colorCnt.get()
        var prevDepth = depthCnt.get()

        while (cnt<6) { //TODO: convert VDI storage also to postRenderLambda

            tGeneration.start = System.nanoTime()

            while (colorCnt.get() == prevColor || depthCnt.get() == prevDepth) {
                Thread.sleep(5)
            }

            prevColor = colorCnt.get()
            prevDepth = depthCnt.get()

            vdiColorBuffer = vdiColor.contents
            vdiDepthBuffer = vdiDepth.contents
            gridCellsBuff = gridCells.contents
            iterationBuffer = vdiIteration.contents

            tGeneration.end = System.nanoTime()

            val timeTaken = (tGeneration.end - tGeneration.start) / 1e9

            logger.info("Time taken for generation (only correct if VDIs were not being written to disk): ${timeTaken}")

            vdiData.metadata.index = cnt

            if (cnt == 4) { //store the 4th VDI

                val file = FileOutputStream(File("VDI_dump$cnt"))
                VDIDataIO.write(vdiData, file)
                logger.info("written the dump")
                file.close()

                SystemHelpers.dumpToFile(vdiColorBuffer!!, "VDI_col")
                SystemHelpers.dumpToFile(vdiDepthBuffer!!, "VDI_depth")
                SystemHelpers.dumpToFile(gridCellsBuff!!, "VDI_octree")
                SystemHelpers.dumpToFile(iterationBuffer!!, "${dataset}_Iterations")

                logger.info("Wrote VDI $cnt")
                VDIsGenerated.incrementAndGet()
            }
            cnt++
        }
        this.close()
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIGenerationBenchmark(1280,720, 20, BenchmarkSetup.Dataset.Rayleigh_Taylor,true).main()
        }
    }
}

