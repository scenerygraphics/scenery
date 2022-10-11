package graphics.scenery.tests.examples.volumes.vdi

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.textures.Texture
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.volumes.*
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDIMetadata
import graphics.scenery.volumes.vdi.VDIVolumeManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.joml.*
import org.scijava.ui.behaviour.ClickBehaviour
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.system.measureNanoTime

/**
 * Class to test volume rendering performance on data loaded from file
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */

data class Timer(var start: Long, var end: Long)

class VDIGenerationExample: SceneryBase("Volume Rendering", 1280, 720, wantREPL = false) {
    var hmd: TrackedStereoGlasses? = null

    val generateVDIs = true
    val separateDepth = true

    val cam: Camera = DetachedHeadCamera(hmd)

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        val volumeManager = VDIVolumeManager.create(generateVDIs, separateDepth, colors32bit = true, scene, hub)

        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            spatial().position = Vector3f(0.0f, 0.0f, 5.0f)
            scene.addChild(this)
        }

        val volume = Volume.fromXML(getDemoFilesPath() + "/volumes/t1-head.xml", hub)
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        volume.setTransferFunctionRange(0.0f, 1500.0f)
        volume.name = "T1Head"
        volume.colormap = Colormap.get("hot")
        volume.spatial().scale = Vector3f(0.3f, 0.3f, 0.6f)
        scene.addChild(volume)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.spatial().position = Vector3f(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            light.intensity = 0.5f
            scene.addChild(light)
        }

        thread {
            if (generateVDIs) {
                manageVDIGeneration()
            }
        }

        thread {
            while(true)
            {
                Thread.sleep(2000)
                println("${cam.spatial().position}")
                println("${cam.spatial().rotation}")
            }
        }
    }

    private fun manageVDIGeneration() {
        val volumeManager = hub.get<VolumeManager>()!!

        var subVDIDepthBuffer: ByteBuffer? = null
        var subVDIColorBuffer: ByteBuffer?
        var gridCellsBuff: ByteBuffer?
        var thresholdBuff: ByteBuffer?

        while(renderer?.firstImageReady == false) {
//        while(renderer?.firstImageReady == false || volumeManager.shaderProperties.isEmpty()) {
            Thread.sleep(50)
        }

        logger.info("First image is ready")

        val subVDIColor = volumeManager.material().textures["OutputSubVDIColor"]!!
        val colorCnt = AtomicInteger(0)

        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (subVDIColor to colorCnt)

        val depthCnt = AtomicInteger(0)
        var subVDIDepth: Texture? = null

        if(separateDepth) {
            subVDIDepth = volumeManager.material().textures["OutputSubVDIDepth"]!!
            (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (subVDIDepth to depthCnt)
        }

        val gridCells = volumeManager.material().textures["OctreeCells"]!!
        val gridTexturesCnt = AtomicInteger(0)

        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (gridCells to gridTexturesCnt)

        val thresholds = volumeManager.material().textures["Thresholds"]!!
        val thresholdCnt = AtomicInteger(0)

        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add (thresholds to thresholdCnt)

        var prevColor = colorCnt.get()
        var prevDepth = depthCnt.get()

        var cnt = 0

        val tGeneration = Timer(0, 0)


        while (true) {
            val volumeManager = hub.get<VolumeManager>()!!
            tGeneration.start = System.nanoTime()
            while(colorCnt.get() == prevColor || depthCnt.get() == prevDepth) {
                Thread.sleep(5)
            }

            logger.info("Found texture number: ${colorCnt.get()}")

            prevColor = colorCnt.get()
            prevDepth = depthCnt.get()
            subVDIColorBuffer = subVDIColor.contents!!.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            if(separateDepth) {
                subVDIDepthBuffer = subVDIDepth!!.contents!!.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            }
            gridCellsBuff = gridCells.contents!!.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            thresholdBuff = thresholds.contents!!.duplicate().order(ByteOrder.LITTLE_ENDIAN)

            tGeneration.end = System.nanoTime()

            val timeTaken = (tGeneration.end - tGeneration.start)/10e9

            logger.info("Time taken for generation (only correct if VDIs were not being written to disk): ${timeTaken}")



            val camera = cam
            val model = volumeManager.nodes.first().spatial().world


            logger.info("The model matrix added to the vdi is: $model.")

            if(cnt < 20) {

                logger.info(volumeManager.shaderProperties.keys.joinToString())
//                val vdiData = VDIData(subVDIDepthBuffer!!, subVDIColorBuffer!!, gridCellsBuff!!, VDIMetadata(

                val fileName = "${volumeManager.nodes.first().name}VDI${cnt}_ndc"
                val total_duration = measureNanoTime {
                    val vdiData = VDIData(
                        VDIMetadata(
                            projection = camera.spatial().projection,
                            view = camera.spatial().getTransformation(),
                            volumeDimensions = Vector3f(volumeManager.nodes.first().getDimensions()),
                            model = model,
                            nw = volumeManager.shaderProperties["nw"] as Float,
                            windowDimensions = Vector2i(camera.width, camera.height)
                        )
                    )

                    val duration = measureNanoTime {
//                        val file = FileOutputStream(File("${fileName}.meta"))
//                    val comp = GZIPOutputStream(file, 65536)
//                        VDIDataIO.write(vdiData, file)
//                        logger.info("written the dump")
//                        file.close()
                    }
                    logger.info("time taken (uncompressed): ${duration}")
                }

                logger.info("total serialization duration: ${total_duration}")

                if(separateDepth) {
                    SystemHelpers.dumpToFile(subVDIColorBuffer!!, "${fileName}_col")
                    SystemHelpers.dumpToFile(subVDIDepthBuffer!!, "${fileName}_depth")
                    SystemHelpers.dumpToFile(gridCellsBuff!!, "${fileName}_octree")
                    SystemHelpers.dumpToFile(thresholdBuff!!, "${fileName}_thresholds")
                } else {
                    SystemHelpers.dumpToFile(subVDIColorBuffer!!, fileName)
                    SystemHelpers.dumpToFile(gridCellsBuff!!, "${fileName}_octree")
                }
                logger.info("Wrote VDI $cnt")
            }
            cnt++

            Thread.sleep(500)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun inputSetup() {
        super.inputSetup()

        inputHandler?.addBehaviour("save_texture", ClickBehaviour { _, _ ->
            logger.info("Finding node")
            val vm = hub.get<VolumeManager>()!!
            val texture = vm.materialOrNull()?.textures?.get("OutputSubVDIColor") ?: return@ClickBehaviour
            val r = renderer ?: return@ClickBehaviour
            logger.info("Node found, saving texture")

            val result = r.requestTexture(texture) { tex ->
                logger.info("Received texture")

                tex.contents?.let { buffer ->
                    logger.info("Dumping to file")
                    SystemHelpers.dumpToFile(buffer, "vdicolor-texture-${SystemHelpers.formatDateTime(delimiter = "_")}.raw")
                    logger.info("File dumped")
                }

            }
            result.getCompleted()

        })
        inputHandler?.addKeyBinding("save_texture", "E")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIGenerationExample().main()
        }
    }
}

