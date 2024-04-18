package graphics.scenery.volumes.vdi

import graphics.scenery.Camera
import graphics.scenery.Settings
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.DataCompressor
import graphics.scenery.utils.extensions.fetchFromGPU
import graphics.scenery.utils.lazyLogger
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.VolumeManager
import org.jetbrains.annotations.ApiStatus.Experimental
import org.joml.Vector2i
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.system.measureNanoTime

/**
 * Class to support streaming of Volumetric Depth Images (VDIs). Provides public functions to stream generated VDIs on the
 * server side and to receive and update them on the client side.
 */
@Experimental
class VDIStreamer {

    private val logger by lazyLogger()

    /** param to determine the state of vdi streaming */
    var vdiStreaming: AtomicBoolean = AtomicBoolean(false)

    /** the number of VDIs streamed so far */
    private var vdisStreamed: Int = 0

    /** is this the first VDI received so far? */
    private var firstVDIReceived = true

    /** the ZMQ context with 4 threads used for publishing the VDI */
    private val context: ZContext = ZContext(4)

    private fun createPublisher(context: ZContext, address : String) : ZMQ.Socket {
        val publisher: ZMQ.Socket = context.createSocket(SocketType.PUB)
        publisher.isConflate = true

        try {
            logger.warn(address)
            publisher.bind(address)
            address.substringAfterLast(":").toInt()
        } catch (e: ZMQException) {
            logger.warn("Binding failed, trying random port: $e")
            publisher.bindToRandomPort(address.substringBeforeLast(":"))
        }
        return publisher
    }

    /**
     * Sets up streaming of VDIs to a chosen [ipAddress].
     *
     * @param[ipAddress] The network address (IP address and port number) to which the VDIs should be streamed
     * @param[cam] The camera that is generating the VDI
     * @param[volumeDim] The dimensions of [volume] on which the VDIs are being generated
     * @param[volume] The volume on which VDIs are being generated
     * @param[maxSupersegments] The maximum number of supersegments in any list of the VDI, i.e., its resolution along z
     * @param[vdiVolumeManager] The [VolumeManager] set up to generate VDIs
     * @param[renderer] The renderer for this application
     */
    fun setup(
        ipAddress: String,
        cam: Camera,
        volumeDim: Vector3f,
        volume: Volume,
        maxSupersegments: Int,
        vdiVolumeManager: VolumeManager,
        renderer: Renderer
    ) {

        val vdiData = VDIData(
            VDIBufferSizes(),
            VDIMetadata(
                volumeDimensions = volumeDim,
            )
        )

        var firstFrame = true

        val publisher = createPublisher(context, ipAddress)

        var compressedColor:  ByteBuffer? = null
        var compressedDepth: ByteBuffer? = null

        val compressionTool = DataCompressor.CompressionTool.LZ4
        val compressor = DataCompressor(compressionTool)

        var vdiColorBuffer: ByteBuffer?
        var vdiDepthBuffer: ByteBuffer?
        var gridCellsBuff: ByteBuffer?

        val vdiColor = vdiVolumeManager.material().textures[VDIVolumeManager.colorTextureName]!!

        val vdiDepth = vdiVolumeManager.material().textures[VDIVolumeManager.depthTextureName]!!

        val gridCells = vdiVolumeManager.material().textures[VDIVolumeManager.accelerationTextureName]!!

        renderer.runAfterRendering.add {

            if (!firstFrame && vdiStreaming.get()) {

                vdiColor.fetchFromGPU()
                vdiDepth.fetchFromGPU()
                gridCells.fetchFromGPU()

                val model = volume.spatial().world

                vdiData.metadata.model = model
                vdiData.metadata.index = vdisStreamed
                vdiData.metadata.projection = cam.spatial().projection
                vdiData.metadata.view = cam.spatial().getTransformation()
                vdiData.metadata.windowDimensions = Vector2i(cam.width, cam.height)
                vdiData.metadata.nw = vdiVolumeManager.shaderProperties["nw"] as Float

                vdiColorBuffer = vdiColor.contents
                vdiDepthBuffer = vdiDepth.contents
                gridCellsBuff = gridCells.contents

                val colorSize = cam.height * cam.width * maxSupersegments * 4 * 4
                val depthSize = cam.width * cam.height * maxSupersegments * 4 * 2
                val accelSize = (cam.width / 8) * (cam.height / 8) * maxSupersegments * 4

                if (vdiColorBuffer!!.remaining() != colorSize || vdiDepthBuffer!!.remaining() != depthSize || gridCellsBuff!!.remaining() != accelSize) {
                    logger.warn("Skipping transmission this frame due to inconsistency in buffer size")
                    logger.warn("Size of color buffer: ${vdiColorBuffer!!.remaining()} and expected size $colorSize")
                    logger.warn("Size of color buffer: ${vdiDepthBuffer!!.remaining()} and expected size $depthSize")
                    logger.warn("Size of color buffer: ${gridCellsBuff!!.remaining()} and expected size $accelSize")
                } else {

                    if(Settings().get("Debug", false)) {
                        val floatBuffer = vdiColorBuffer!!.asFloatBuffer()
                        var cnt = 0
                        while (floatBuffer.remaining() > 0) {
                            val t = floatBuffer.get()
                            if(t != 0f) {
                                cnt++
                            }
                        }
                        if(cnt == 0) {
                            logger.warn("VDI color buffer only contains 0s.")
                        }
                    }

                    if (compressedColor == null) {
                        compressedColor =
                            MemoryUtil.memAlloc(compressor.returnCompressBound(colorSize.toLong()))
                    }

                    val compressedColorLength =
                        compressor.compress(compressedColor!!, vdiColorBuffer!!, 3)
                    compressedColor!!.limit(compressedColorLength.toInt())
                    vdiData.bufferSizes.colorSize = compressedColorLength

                    if (compressedDepth == null) {
                        compressedDepth =
                            MemoryUtil.memAlloc(compressor.returnCompressBound(depthSize.toLong()))
                    }

                    val compressedDepthLength =
                        compressor.compress(compressedDepth!!, vdiDepthBuffer!!, 3)
                    compressedDepth!!.limit(compressedDepthLength.toInt())
                    vdiData.bufferSizes.depthSize = compressedDepthLength

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

                    gridCellsBuff!!.get(
                        message, vdiDataSize.size + metadataBytes.size + compressedColor!!.remaining() +
                            compressedDepth!!.remaining(), gridCellsBuff!!.remaining()
                    )
                    gridCellsBuff!!.flip()

                    compressedDepth!!.limit(compressedDepth!!.capacity())
                    compressedColor!!.limit(compressedColor!!.capacity())

                    val sent = publisher.send(message)
                    if (!sent) {
                        logger.warn("There was a ZeroMQ error in queuing the VDI")
                    } else {
                        vdisStreamed += 1
                    }
                }
            }
            firstFrame = false
        }
    }

    private fun decompress(
        payload: ByteArray,
        compressedColor: ByteBuffer,
        compressedDepth: ByteBuffer,
        accelGridBuffer: ByteBuffer,
        colorBuffer: ByteBuffer,
        depthBuffer: ByteBuffer,
        colorSize: Int,
        depthSize: Int,
        compressor: DataCompressor
    ): VDIData {

        val metadataSize = payload.sliceArray(0 until 3).toString(Charsets.US_ASCII).toInt() //hardcoded 3 digit number
        val metadata = ByteArrayInputStream(payload.sliceArray(3 until (metadataSize + 3)))
        val vdiData = VDIDataIO.read(metadata)
        logger.info("Index of received VDI: ${vdiData.metadata.index}")

        val compressedColorLength = vdiData.bufferSizes.colorSize
        val compressedDepthLength = vdiData.bufferSizes.depthSize

        compressedColor.put(payload.sliceArray((metadataSize + 3) until (metadataSize + 3 + compressedColorLength.toInt())))
        compressedColor.flip()
        compressedDepth.put(payload.sliceArray((metadataSize + 3) + compressedColorLength.toInt() until (metadataSize + 3) + compressedColorLength.toInt() + compressedDepthLength.toInt()))
        compressedDepth.flip()

        accelGridBuffer.put(payload.sliceArray((metadataSize + 3) + compressedColorLength.toInt() + compressedDepthLength.toInt() until payload.size))
        accelGridBuffer.flip()

        val colorDone = AtomicInteger(0)

        thread {
            compressedColor.limit(compressedColorLength.toInt())
            val decompressedColorLength = compressor.decompress(colorBuffer, compressedColor.slice())
            compressedColor.limit(compressedColor.capacity())
            if (decompressedColorLength.toInt() != colorSize) {
                logger.warn("Error decompressing color message. Decompressed length: $decompressedColorLength and desired size: $colorSize")
            }
            colorDone.incrementAndGet()
        }

        compressedDepth.limit(compressedDepthLength.toInt())
        val decompressedDepthLength = compressor.decompress(depthBuffer, compressedDepth.slice())
        compressedDepth.limit(compressedDepth.capacity())
        if (decompressedDepthLength.toInt() != depthSize) {
            logger.warn("Error decompressing depth message. Decompressed length: $decompressedDepthLength and desired size: $depthSize")
        }

        while (colorDone.get() == 0) {
            Thread.sleep(20)
        }

        colorBuffer.limit(colorSize)

        return vdiData
    }

    /**
     * Receives VDIs from a network stream and replaces them in the scene.
     *
     * The function runs blocking to receive and update successive VDIs transmitted across the network.
     *
     * @param[vdiNode] The [VDINode] that is part of the scene to be rendered
     * @param[address] The network address (name/IP address and port number) from which to receive the VDIs
     * @param[renderer] The renderer for this application
     * @param[windowWidth] Window width of the application window.
     * @param[windowHeight] Window height of the application window.
     * @param[numSupersegments] The maximum number of supersegments in any list of the VDI, i.e., its resolution along z
     */
    fun receiveAndUpdate(
        vdiNode: VDINode,
        address: String,
        renderer: Renderer,
        windowWidth: Int,
        windowHeight: Int,
        numSupersegments: Int
    ) {
        val subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
        subscriber.isConflate = true
        try {
            subscriber.connect(address)
        } catch (e: ZMQException) {
            logger.warn("ZMQ Binding failed.")
        }
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)

        //Attaching initial empty textures to the second buffer so that it is not null
        vdiNode.attachEmptyTextures(VDINode.DoubleBuffer.Second)

        val compressionTool = DataCompressor.CompressionTool.LZ4
        val compressor = DataCompressor(compressionTool)

        //the expected sizes of each buffer
        val colorSize = windowWidth * windowHeight * numSupersegments * 4 * 4
        val depthSize = windowWidth * windowHeight * numSupersegments * 2 * 4
        val accelSize = (windowWidth/8) * (windowHeight/8) * numSupersegments * 4

        val decompressionBuffer = 1024

        val colorBuffer = MemoryUtil.memCalloc(colorSize + decompressionBuffer)
        val depthBuffer = MemoryUtil.memCalloc(depthSize + decompressionBuffer)

        val compressedColor: ByteBuffer =
            MemoryUtil.memAlloc(compressor.returnCompressBound(colorSize.toLong()))
        val compressedDepth: ByteBuffer =
            MemoryUtil.memAlloc(compressor.returnCompressBound(depthSize.toLong()))
        val accelGridBuffer =
            MemoryUtil.memAlloc(accelSize)

        var vdiData: VDIData

        while(!renderer.shouldClose) {
            val payload: ByteArray?
            logger.info("Waiting for VDI")

            val receiveTime = measureNanoTime {
                payload = subscriber.recv()
            }
            logger.info("Time taken for the receive: ${receiveTime/1e9}")

            if (payload != null) {
                vdiData = decompress(
                    payload,
                    compressedColor,
                    compressedDepth,
                    accelGridBuffer,
                    colorBuffer,
                    depthBuffer,
                    colorSize,
                    depthSize,
                    compressor
                )

                colorBuffer.limit(colorBuffer.remaining() - decompressionBuffer)
                depthBuffer.limit(depthBuffer.remaining() - decompressionBuffer)

                vdiNode.updateVDI(vdiData, colorBuffer.slice(), depthBuffer.slice(), accelGridBuffer)

                colorBuffer.limit(colorBuffer.capacity())
                depthBuffer.limit(depthBuffer.capacity())

                firstVDIReceived = false
                vdiNode.visible = true
            }
            else {
                logger.info("Payload received but is null")
            }
            logger.info("Received and updated VDI data")
        }
    }
}
