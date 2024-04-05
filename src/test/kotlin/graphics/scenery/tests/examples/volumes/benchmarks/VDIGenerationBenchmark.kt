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
import graphics.scenery.utils.extensions.positionVolumeSlices
import graphics.scenery.volumes.*
import graphics.scenery.volumes.vdi.benchmarks.BenchmarkSetup
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.*
import org.zeromq.ZContext
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

public class VDIGenerationBenchmark (wWidth: Int = 512, wHeight: Int = 512, val maxSupersegments: Int = 20, val dataset: BenchmarkSetup.Dataset, val fetchVDI: Boolean) : SceneryBase("Volume Generation Example", wWidth, wHeight) {
    val context: ZContext = ZContext(4)

    var cnt = 0

    val cam: Camera = DetachedHeadCamera()

    var writeVDIs: AtomicBoolean = AtomicBoolean(false)
    val VDIsGenerated = AtomicInteger(0)

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


        // Step 2: Create VDI Volume Manager
        val vdiVolumeManager = VDIVolumeManager( hub, windowWidth, windowHeight, maxSupersegments, scene).createVDIVolumeManager()

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

        parent.positionVolumeSlices(volumeList)

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
        if (fetchVDI) {
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
        var thresholdBuffer: ByteBuffer?

        val volumeList = ArrayList<BufferedVolume>()
        volumeList.add(vdiVolumeManager.nodes.first() as BufferedVolume)
        while (renderer?.firstImageReady == false) {
            Thread.sleep(50)
        }

        val vdiColor = vdiVolumeManager.material().textures[VDIVolumeManager.colorTextureName]!!
        val colorCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiColor to colorCnt)

        val vdiDepth = vdiVolumeManager.material().textures[VDIVolumeManager.depthTextureName]!!
        val depthCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiDepth to depthCnt)


        val gridCells = vdiVolumeManager.material().textures[VDIVolumeManager.accelerationTextureName]!!
        val gridTexturesCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(gridCells to gridTexturesCnt)

        renderer!!.runAfterRendering.add {

            vdiData.metadata.projection = cam.spatial().projection
            vdiData.metadata.view = cam.spatial().getTransformation()

            vdiColorBuffer = vdiColor.contents
            vdiDepthBuffer = vdiDepth.contents
            gridCellsBuff = gridCells.contents

            tGeneration.end = System.nanoTime()

            val timeTaken = (tGeneration.end - tGeneration.start) / 1e9

            logger.info("Time taken for generation (only correct if VDIs were not being written to disk): ${timeTaken}")

            vdiData.metadata.index = cnt

            if (writeVDIs.get() == true) { //store the 4th VDI

                val filePrefix = dataset.toString() + "_${windowWidth}_${windowHeight}_${maxSupersegments}"

                val file = FileOutputStream(File("${filePrefix}_${VDIsGenerated.get()}.vdi-metadata"))
                VDIDataIO.write(vdiData, file)
                logger.info("written the dump")
                file.close()

                SystemHelpers.dumpToFile(vdiColorBuffer!!, "${filePrefix}_${VDIsGenerated.get()}.vdi-color")
                SystemHelpers.dumpToFile(vdiDepthBuffer!!, "${filePrefix}_${VDIsGenerated.get()}.vdi-depth")
                SystemHelpers.dumpToFile(gridCellsBuff!!, "${filePrefix}_${VDIsGenerated.get()}.vdi-grid")

                logger.info("Wrote VDI ${VDIsGenerated.get()}")
                VDIsGenerated.incrementAndGet()
            }
            cnt++
            tGeneration.start = System.nanoTime()
        }
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIGenerationBenchmark(1280,720, 20, BenchmarkSetup.Dataset.Kingsnake,true).main()
        }
    }
}

