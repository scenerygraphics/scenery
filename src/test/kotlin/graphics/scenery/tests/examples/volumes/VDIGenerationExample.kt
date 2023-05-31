package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.volumes.VolumeManager
import tpietzsch.shadergen.generate.SegmentTemplate
import tpietzsch.shadergen.generate.SegmentType
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.VolumeCommons
import graphics.scenery.volumes.vdi.*
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import com.fasterxml.jackson.databind.ObjectMapper
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.volumes.*
import org.joml.*
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.*
import java.nio.ByteBuffer



class VDIGenerationExample : SceneryBase("Volume Manager Switching Example", 512, 512) {

    val maxSupersegments = System.getProperty("VolumeBenchmark.NumSupersegments")?.toInt()?: 20
    val context: ZContext = ZContext(4)

    override fun init() {

        // Step 1: Create Volume
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
        val vdiVolumeManager =  vdiFull(windowWidth, windowHeight, maxSupersegments, scene, hub)
        logger.warn("222222222 vdi VM " + vdiVolumeManager.getUuid().toString())
        logger.warn("222222222 vdi VM in init: " + hub.get<VolumeManager>()?.getUuid())

//        // Step 3: add volume to vdi vm
        hub.get<VolumeManager>()?.add(volume)

        // Step 4: Vdi volume manager add volume
        vdiVolumeManager.add(volume)

        // Step 5: Vdi volume manager add volume
        hub.add(vdiVolumeManager)

        // Step 3: Store VDI Generated
        storeVDI(vdiVolumeManager)



    }

    private fun instantiateVolumeManager(raycastShader: String, accumulateShader: String, hub: Hub): VolumeManager {

        return VolumeManager(
            hub, useCompute = true,
            customSegments = hashMapOf(
                SegmentType.FragmentShader to SegmentTemplate(
                    this::class.java,
                    raycastShader,
                    "intersectBoundingBox", "vis", "localNear", "localFar", "SampleVolume", "Convert", "Accumulate",
                ),
                SegmentType.Accumulator to SegmentTemplate(
//                                this.javaClass,
                    accumulateShader,
                    "vis", "localNear", "localFar", "sampleVolume", "convert",
                ),
            ),
        )
    }

    private fun vdiFull(windowWidth: Int, windowHeight: Int, maxSupersegments: Int, scene: Scene, hub: Hub): VolumeManager {
        val raycastShader = "VDIGenerator.comp"
        val accumulateShader = "AccumulateVDI.comp"
        val volumeManager = instantiateVolumeManager(raycastShader, accumulateShader, hub)

        val outputSubColorBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*4*maxSupersegments * 4)

        val outputSubDepthBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*2*maxSupersegments*2 * 2)

//            val numGridCells = 2.0.pow(numOctreeLayers)
        val numGridCells = Vector3f(windowWidth.toFloat() / 8f, windowHeight.toFloat() / 8f, maxSupersegments.toFloat())
//            val numGridCells = Vector3f(256f, 256f, 256f)
        val lowestLevel = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)

        val thresholdsBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)
        val numGeneratedBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)

        val outputSubVDIColor: Texture
        val outputSubVDIDepth: Texture
        val gridCells: Texture
        val thresholdsArray: Texture
        val numGenerated: Texture

        outputSubVDIColor = Texture.fromImage(
            Image(outputSubColorBuffer, maxSupersegments, windowHeight, windowWidth), usage = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 4, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)


        volumeManager.customTextures.add("OutputSubVDIColor")
        volumeManager.material().textures["OutputSubVDIColor"] = outputSubVDIColor

        outputSubVDIDepth = Texture.fromImage(
            Image(outputSubDepthBuffer, 2 * maxSupersegments, windowHeight, windowWidth),  usage = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
//                    Image(outputSubDepthBuffer, 2*maxSupersegments, windowHeight, windowWidth),  usage = hashSetOf(
//                        Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        volumeManager.customTextures.add("OutputSubVDIDepth")
        volumeManager.material().textures["OutputSubVDIDepth"] = outputSubVDIDepth


        gridCells = Texture.fromImage(
            Image(lowestLevel, numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), channels = 1, type = UnsignedIntType(),
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        volumeManager.customTextures.add("OctreeCells")
        volumeManager.material().textures["OctreeCells"] = gridCells

        volumeManager.customUniforms.add("doGeneration")
        volumeManager.shaderProperties["doGeneration"] = true

        val compute = RichNode()
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("GridCellsToZero.comp"), this::class.java)))

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), 1),
            invocationType = InvocationType.Permanent
        )

        compute.material().textures["GridCells"] = gridCells

        scene.addChild(compute)

        return volumeManager
    }

    private fun storeVDI(vdiVolumeManager: VolumeManager) {
        data class Timer(var start: Long, var end: Long)

        val storeVDIs = true
        var subVDIDepthBuffer: ByteBuffer? = null
        var subVDIColorBuffer: ByteBuffer?
        var gridCellsBuff: ByteBuffer?
        val separateDepth = true
        val world_abs = false

        val volumeList = ArrayList<BufferedVolume>()
        val VDIsGenerated = AtomicInteger(0)

        val dataset = System.getProperty("VolumeBenchmark.Dataset")?.toString() ?: "Simulation"
        val vo = System.getProperty("VolumeBenchmark.Vo")?.toFloat()?.toInt() ?: 0

        while (renderer?.firstImageReady == false) {
            while (renderer?.firstImageReady == false || vdiVolumeManager.shaderProperties.isEmpty()) {
                Thread.sleep(50)
            }

            val subVDIColor = vdiVolumeManager.material().textures["OutputSubVDIColor"]!!
            val colorCnt = AtomicInteger(0)

            (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(subVDIColor to colorCnt)

            val depthCnt = AtomicInteger(0)
            var subVDIDepth: Texture? = null

            if (separateDepth) {
                subVDIDepth = vdiVolumeManager.material().textures["OutputSubVDIDepth"]!!
                (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(subVDIDepth to depthCnt)
            }

            val gridCells = vdiVolumeManager.material().textures["OctreeCells"]!!
            val gridTexturesCnt = AtomicInteger(0)

            (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(gridCells to gridTexturesCnt)

            var prevColor = colorCnt.get()
            var prevDepth = depthCnt.get()

            var cnt = 0

            val tGeneration = Timer(0, 0)

            val publisher = createPublisher()

            val objectMapper = ObjectMapper(MessagePackFactory())

            var compressedColor: ByteBuffer? = null
            var compressedDepth: ByteBuffer? = null

            val compressor = VDICompressor()
            val compressionTool = VDICompressor.CompressionTool.LZ4

            var firstFrame = true

            var volumeCommons: VolumeCommons = VolumeCommons(windowWidth, windowHeight, dataset, logger)

            while (storeVDIs) { //TODO: convert VDI storage also to postRenderLambda

                tGeneration.start = System.nanoTime()
                while (colorCnt.get() == prevColor || depthCnt.get() == prevDepth) {
                    Thread.sleep(5)
                }
                prevColor = colorCnt.get()
                prevDepth = depthCnt.get()
                subVDIColorBuffer = subVDIColor.contents
                if (separateDepth) {
                    subVDIDepthBuffer = subVDIDepth!!.contents
                }
                gridCellsBuff = gridCells.contents

                tGeneration.end = System.nanoTime()

                val timeTaken = (tGeneration.end - tGeneration.start) / 1e9

                logger.info("Time taken for generation (only correct if VDIs were not being written to disk): ${timeTaken}")

                val model = volumeList.first().spatial().world

                val vdiData = VDIData(
                    VDIBufferSizes(),
                    VDIMetadata(
                        index = cnt,
//                        projection = camera.spatial().projection,
//                        view = camera.spatial().getTransformation(),
                        volumeDimensions = volumeCommons.volumeDims,
                        model = model,
                        nw = volumeList.first().volumeManager.shaderProperties["nw"] as Float,
//                        windowDimensions = Vector2i(camera.width, camera.height)
                    )
                )

                if (storeVDIs) {
                    if (cnt == 4) { //store the 4th VDI
                        val file = FileOutputStream(File("${dataset}vdi_${windowWidth}_${windowHeight}_${maxSupersegments}_${vo}_dump$cnt"))
                        //                    val comp = GZIPOutputStream(file, 65536)
                        VDIDataIO.write(vdiData, file)
                        logger.info("written the dump")
                        file.close()

                        var fileName = ""
                        if (world_abs) {
                            fileName = "${dataset}VDI_${windowWidth}_${windowHeight}_${maxSupersegments}_${vo}_${cnt}_world_new"
                        } else {
                            fileName = "${dataset}VDI_${windowWidth}_${windowHeight}_${maxSupersegments}_${vo}_${cnt}_ndc"
                        }
                        if (separateDepth) {
                            SystemHelpers.dumpToFile(subVDIColorBuffer!!, "${fileName}_col")
                            SystemHelpers.dumpToFile(subVDIDepthBuffer!!, "${fileName}_depth")
                            SystemHelpers.dumpToFile(gridCellsBuff!!, "${fileName}_octree")
                        } else {
                            SystemHelpers.dumpToFile(subVDIColorBuffer!!, fileName)
                            SystemHelpers.dumpToFile(gridCellsBuff!!, "${fileName}_octree")
                        }
                        logger.info("Wrote VDI $cnt")
                        VDIsGenerated.incrementAndGet()
                    }
                }
                cnt++
            }
        }
    }


        fun createPublisher() : ZMQ.Socket {
        var publisher: ZMQ.Socket = context.createSocket(SocketType.PUB)
        publisher.isConflate = true

        val address: String = "tcp://0.0.0.0:6655"
        val port = try {
            publisher.bind(address)
            address.substringAfterLast(":").toInt()
        } catch (e: ZMQException) {
            logger.warn("Binding failed, trying random port: $e")
            publisher.bindToRandomPort(address.substringBeforeLast(":"))
        }

        return publisher
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIGenerationExample().main()
        }
    }

}
