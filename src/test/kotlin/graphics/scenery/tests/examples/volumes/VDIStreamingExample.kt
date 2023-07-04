package graphics.scenery.tests.examples.volumes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.SceneryBase
import graphics.scenery.volumes.vdi.VDIDataIO
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.volumes.*
import graphics.scenery.volumes.vdi.VDIBufferSizes
import graphics.scenery.utils.DataCompressor
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDIMetadata
import org.joml.Quaternionf
import org.joml.Vector2i
import org.joml.Vector3f
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import org.lwjgl.system.MemoryUtil
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.*
import kotlin.system.measureNanoTime
import org.msgpack.jackson.dataformat.MessagePackFactory

class VDIStreamingExample : SceneryBase("VDI Streaming Example", 512, 512) {

    val cam: Camera = DetachedHeadCamera()
    val context: ZContext = ZContext(4)

    val maxSupersegments = System.getProperty("VolumeBenchmark.NumSupersegments")?.toInt()?: 20
    var cnt = 0

    var firstFrame = true
    val generateVDIs = true


    override fun init() {

        //Step 1: create necessary components: camera, volume, volumeManager
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
        val vdiVolumeManager = VDIVolumeManager( hub, windowWidth, windowHeight, maxSupersegments, scene).createVDIVolumeManger()

        //step 3: switch the volume's current volume manager to VDI volume manager
        volume.volumeManager = vdiVolumeManager

        // Step 4: add the volume to VDI volume manager
        vdiVolumeManager.add(volume)

        // Step 5: add the VDI volume manager to the hub
        hub.add(vdiVolumeManager)


        //Step  6: transmitting the VDI
        settings.set("VideoEncoder.StreamVideo", true)
        settings.set("VideoEncoder.StreamingAddress", "rtp://10.1.33.211:5004")
        renderer?.recordMovie()
        setupSubscription()

        val volumeDimensions3i = Vector3f(volume.getDimensions().x.toFloat(),volume.getDimensions().y.toFloat(),volume.getDimensions().z.toFloat())
        val model = volume.spatial().world

        val vdiData = VDIData(
            VDIBufferSizes(),
            VDIMetadata(
                index = cnt,
                projection = cam.spatial().projection,
                view = cam.spatial().getTransformation(),
                volumeDimensions = volumeDimensions3i,
                model = model,
                nw = volume.volumeManager.shaderProperties["nw"] as Float,
                windowDimensions = Vector2i(cam.width, cam.height)
            )
        )

        transmitVDI(vdiVolumeManager, vdiData)
    }

    fun transmitVDI(vdiVolumeManager: VolumeManager, vdiData: VDIData) {
        
        val publisher = createPublisher()

        var compressedColor:  ByteBuffer? = null
        var compressedDepth: ByteBuffer? = null

        val compressor = DataCompressor()
        val compressionTool = DataCompressor.CompressionTool.LZ4

        var vdiColorBuffer: ByteBuffer?
        var vdiDepthBuffer: ByteBuffer? = null
        var gridCellsBuff: ByteBuffer?

        val vdiColor = vdiVolumeManager.material().textures["OutputSubVDIColor"]!!
        val colorCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiColor to colorCnt)

        val vdiDepth = vdiVolumeManager.material().textures["OutputSubVDIDepth"]!!
        val depthCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(vdiDepth to depthCnt)


        val gridCells = vdiVolumeManager.material().textures["OctreeCells"]!!
        val gridTexturesCnt = AtomicInteger(0)
        (renderer as? VulkanRenderer)?.persistentTextureRequests?.add(gridCells to gridTexturesCnt)

        (renderer as? VulkanRenderer)?.postRenderLambdas?.add {

            if (!firstFrame) {

                vdiColorBuffer = vdiColor.contents
                vdiDepthBuffer = vdiDepth!!.contents
                gridCellsBuff = gridCells.contents

                val colorSize = windowHeight * windowWidth * maxSupersegments * 4 * 4
                val depthSize = windowWidth * windowHeight * maxSupersegments * 4 * 2

                val accelSize = (windowWidth / 8) * (windowHeight / 8) * maxSupersegments * 4

                if (vdiColorBuffer!!.remaining() != colorSize || vdiDepthBuffer!!.remaining() != depthSize || gridCellsBuff!!.remaining() != accelSize) {
                    logger.warn("Skipping transmission this frame due to inconsistency in buffer size")
                }

                val compressionTime = measureNanoTime {
                    if (compressedColor == null) {
                        compressedColor =
                            MemoryUtil.memAlloc(compressor.returnCompressBound(colorSize.toLong(), compressionTool))
                    }
                    val compressedColorLength =
                        compressor.compress(compressedColor!!, vdiColorBuffer!!, 3, compressionTool)
                    compressedColor!!.limit(compressedColorLength.toInt())

                    vdiData.bufferSizes.colorSize = compressedColorLength

                    if (compressedDepth == null) {
                        compressedDepth =
                            MemoryUtil.memAlloc(
                                compressor.returnCompressBound(
                                    depthSize.toLong(),
                                    compressionTool
                                )
                            )
                    }
                    val compressedDepthLength =
                        compressor.compress(compressedDepth!!, vdiDepthBuffer!!, 3, compressionTool)
                    compressedDepth!!.limit(compressedDepthLength.toInt())

                    vdiData.bufferSizes.depthSize = compressedDepthLength
                }

                logger.info("Time taken in compressing VDI: ${compressionTime / 1e9}")

                val publishTime = measureNanoTime {
                    val metadataOut = ByteArrayOutputStream()
                    VDIDataIO.write(vdiData, metadataOut)

                    val metadataBytes = metadataOut.toByteArray()
                    logger.info("Size of VDI data is: ${metadataBytes.size}")

                    val vdiDataSize = metadataBytes.size.toString().toByteArray(Charsets.US_ASCII)

                    var messageLength = vdiDataSize.size + metadataBytes.size + compressedColor!!.remaining()
                    messageLength += compressedDepth!!.remaining()
                    messageLength += accelSize

                    val message = ByteArray(messageLength)
                    vdiDataSize.copyInto(message)

                    metadataBytes.copyInto(message, vdiDataSize.size)

                    compressedColor!!.slice()
                        .get(message, vdiDataSize.size + metadataBytes.size, compressedColor!!.remaining())

                    compressedDepth!!.slice().get(
                        message,
                        vdiDataSize.size + metadataBytes.size + compressedColor!!.remaining(),
                        compressedDepth!!.remaining()
                    )

                    vdiData.bufferSizes.accelGridSize = accelSize.toLong()

                    gridCellsBuff!!.get(message, vdiDataSize.size + metadataBytes.size + compressedColor!!.remaining() +
                        compressedDepth!!.remaining(), gridCellsBuff!!.remaining())
                    gridCellsBuff!!.flip()

                    compressedDepth!!.limit(compressedDepth!!.capacity())

                    compressedColor!!.limit(compressedColor!!.capacity())

                    val sent = publisher.send(message)

                    if (!sent) {
                        logger.warn("There was a ZeroMQ error in queuing the message to send")
                    }
                }
                logger.info("Whole publishing process took: ${publishTime / 1e9}")
            }
            firstFrame = false
        }
        setupSubscription()
    }

    private fun createPublisher() : ZMQ.Socket {
        var publisher: ZMQ.Socket = context.createSocket(SocketType.PUB)
        publisher.isConflate = true
        val address = "tcp://localhost:6655"
//        val address = "tcp://0.0.0.0:6655"
        val port = try {
            publisher.bind(address)
            address.substringAfterLast(":").toInt()
        } catch (e: ZMQException) {
            logger.warn("Binding failed, trying random port: $e")
            publisher.bindToRandomPort(address.substringBeforeLast(":"))
        }

        return publisher
    }

    fun setupSubscription() {
        val subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
        subscriber.isConflate = true
//     val address = "tcp://localhost:6655"
        val address = "tcp://10.1.33.211:6655"
        //IPADDRESS
        try {
            subscriber.connect(address)
        } catch (e: ZMQException) {
            logger.warn("ZMQ Binding failed.")
        }
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)

        val objectMapper = ObjectMapper(MessagePackFactory())

        var frameCount = 0

        var firstFrame = true

        (renderer as? VulkanRenderer)?.postRenderLambdas?.add {
            if(!firstFrame) {

                if(generateVDIs) {
                    logger.info("rendering is running!")
                }

                val start = System.nanoTime()
                val payload = subscriber.recv(0)

                val end = System.nanoTime()

                logger.info("Time waiting for message: ${(end-start)/1e9}")

                if (payload != null) {
                    val deserialized: List<Any> =
                        objectMapper.readValue(payload, object : TypeReference<List<Any>>() {})

                    logger.info("Applying the camera change: $frameCount!")

                    cam.spatial().rotation = stringToQuaternion(deserialized[0].toString())
                    cam.spatial().position = stringToVector3f(deserialized[1].toString())
                }
                frameCount++
            }
            firstFrame = false
        }
    }

    private fun stringToQuaternion(inputString: String): Quaternionf {
        val elements = inputString.removeSurrounding("[", "]").split(",").map { it.toFloat() }
        return Quaternionf(elements[0], elements[1], elements[2], elements[3])
    }

    private fun stringToVector3f(inputString: String): Vector3f {
        val mElements = inputString.removeSurrounding("[", "]").split(",").map { it.toFloat() }
        return Vector3f(mElements[0], mElements[1], mElements[2])
    }



    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIStreamingExample().main()
        }
    }

}
