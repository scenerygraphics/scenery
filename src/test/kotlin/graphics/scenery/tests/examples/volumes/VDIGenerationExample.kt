package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.volumes.VolumeManager
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.vdi.*
import org.joml.Vector3f
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.volumes.*
import org.joml.*
import java.io.*
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Example application showing how to generate Volumetric Depth Images (VDIs). A [VolumeManager] is setup to generate
 * VDIs and the generated VDIs are written to the file system.
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */
class VDIGenerationExample(wWidth: Int = 512, wHeight: Int = 512, val maxSupersegments: Int = 20) : SceneryBase("Volume Generation Example", wWidth, wHeight) {
    private var count = 0

    override fun init() {

        // Step 1: Create renderer, volume and camera
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.5f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            scene.addChild(this)
        }

        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
        volume.name = "volume"
        volume.colormap = Colormap.get("viridis")
        volume.spatial {
            position = Vector3f(0.0f, 0.0f, -3.5f)
            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
            scale = Vector3f(20.0f, 20.0f, 20.0f)
        }
        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
        scene.addChild(volume)

        // Step 2: Create VDI Volume Manager
        val vdiVolumeManager = VDIVolumeManager( hub, windowWidth, windowHeight, maxSupersegments, scene).createVDIVolumeManager()

        //step 3: switch the volume's current volume manager to VDI volume manager
        volume.volumeManager = vdiVolumeManager

        // Step 4: add the volume to VDI volume manager
        vdiVolumeManager.add(volume)
        volume.volumeManager.shaderProperties["doGeneration"] = true

        // Step 5: add the VDI volume manager to the hub
        hub.add(vdiVolumeManager)

        // Step 6: Store VDI Generated
        val volumeDimensions3i = Vector3f(volume.getDimensions().x.toFloat(),volume.getDimensions().y.toFloat(),volume.getDimensions().z.toFloat())
        val model = volume.spatial().world

        val vdiData = VDIData(
            VDIBufferSizes(),
            VDIMetadata(
                index = count,
                projection = cam.spatial().projection,
                view = cam.spatial().getTransformation(),
                volumeDimensions = volumeDimensions3i,
                model = model,
                nw = volume.volumeManager.shaderProperties["nw"] as Float,
                windowDimensions = Vector2i(cam.width, cam.height)
            )
        )

        thread(isDaemon = true) {
            storeVDI(vdiVolumeManager, vdiData)
        }
    }

    private fun storeVDI(vdiVolumeManager: VolumeManager, vdiData: VDIData) {
        data class Timer(var start: Long, var end: Long)
        val tGeneration = Timer(0, 0)

        var vdiDepthBuffer: ByteBuffer?
        var vdiColorBuffer: ByteBuffer?
        var gridCellsBuff: ByteBuffer?

        val volumeList = ArrayList<BufferedVolume>()
        volumeList.add(vdiVolumeManager.nodes.first() as BufferedVolume)
        val vdisGenerated = AtomicInteger(0)
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

        var prevColor = colorCnt.get()
        var prevDepth = depthCnt.get()

        while (count<6) { //TODO: convert VDI storage also to postRenderLambda

            tGeneration.start = System.nanoTime()

            while (colorCnt.get() == prevColor || depthCnt.get() == prevDepth) {
                Thread.sleep(5)
            }

            prevColor = colorCnt.get()
            prevDepth = depthCnt.get()

            vdiColorBuffer = vdiColor.contents
            vdiDepthBuffer = vdiDepth.contents
            gridCellsBuff = gridCells.contents

            tGeneration.end = System.nanoTime()

            val timeTaken = (tGeneration.end - tGeneration.start) / 1e9

            logger.info("Time taken for generation (only correct if VDIs were not being written to disk): ${timeTaken}")

            vdiData.metadata.index = count

            if (count == 4) { //store the 4th VDI

                val vdiFilename = "example$count"
                val file = FileOutputStream(File("$vdiFilename.vdi-metadata"))
                VDIDataIO.write(vdiData, file)
                logger.info("written the dump")
                file.close()

                SystemHelpers.dumpToFile(vdiColorBuffer!!, "$vdiFilename.vdi-color")
                SystemHelpers.dumpToFile(vdiDepthBuffer!!, "$vdiFilename.vdi-depth")
                SystemHelpers.dumpToFile(gridCellsBuff!!, "$vdiFilename.vdi-grid")

                logger.info("Wrote VDI $count")
                vdisGenerated.incrementAndGet()
            }
            count++
        }
        this.close()
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIGenerationExample().main()
        }
    }
}
