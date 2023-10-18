package graphics.scenery.volumes.vdi

import graphics.scenery.Camera
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.textures.Texture
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.utils.DataCompressor
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.utils.lazyLogger
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.VolumeManager
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.system.measureNanoTime

class VDIStreamer {

    private val logger by lazyLogger()

    /** param to determine the state of vdi streaming */
    var vdiStreaming: Boolean = true

    /** the number of VDIs streamed so far */
    var vdisStreamed: Int = 0

    /** is this the first VDI received so far? */
    var firstVDIReceived = true

    /** the ZMQ context with 4 threads used for publishing the VDI */
    val context: ZContext = ZContext(4)

    private fun createPublisher(context: ZContext, IPAddress : String) : ZMQ.Socket {
        val publisher: ZMQ.Socket = context.createSocket(SocketType.PUB)
        publisher.isConflate = true
        val address = IPAddress
        val port = try {
            logger.warn(IPAddress)
            publisher.bind(address)
            address.substringAfterLast(":").toInt()
        } catch (e: ZMQException) {
            logger.warn("Binding failed, trying random port: $e")
            publisher.bindToRandomPort(address.substringBeforeLast(":"))
        }
        return publisher
    }

    fun streamVDI(ipAddress: String, cam: Camera, volumeDim: Vector3f, volume: Volume,
                  maxSupersegments : Int, vdiVolumeManager: VolumeManager, renderer: Renderer) {

        val vdiData = VDIData(
            VDIBufferSizes(),
            VDIMetadata(
                volumeDimensions = volumeDim,
                nw = vdiVolumeManager.shaderProperties["nw"] as Float,
            )
        )

        var firstFrame = true

        val windowWidth = cam.width
        val windowHeight = cam.height

        val publisher = createPublisher(context, ipAddress)

        var compressedColor:  ByteBuffer? = null
        var compressedDepth: ByteBuffer? = null

        val compressor = DataCompressor()
        val compressionTool = DataCompressor.CompressionTool.LZ4

        var vdiColorBuffer: ByteBuffer?
        var vdiDepthBuffer: ByteBuffer?
        var gridCellsBuff: ByteBuffer?

        val vdiColor = vdiVolumeManager.material().textures["OutputSubVDIColor"]!!
        val colorCnt = AtomicInteger(0)
        (renderer as VulkanRenderer).persistentTextureRequests.add(vdiColor to colorCnt)

        val vdiDepth = vdiVolumeManager.material().textures["OutputSubVDIDepth"]!!
        val depthCnt = AtomicInteger(0)
        renderer.persistentTextureRequests.add(vdiDepth to depthCnt)


        val gridCells = vdiVolumeManager.material().textures["OctreeCells"]!!
        val gridTexturesCnt = AtomicInteger(0)
        renderer.persistentTextureRequests.add(gridCells to gridTexturesCnt)

        renderer.postRenderLambdas.add {

            if (!firstFrame && vdiStreaming) {

                val model = volume.spatial().world

                vdiData.metadata.model = model
                vdiData.metadata.index = vdisStreamed
                vdiData.metadata.projection = cam.spatial().projection
                vdiData.metadata.view = cam.spatial().getTransformation()
                vdiData.metadata.windowDimensions = Vector2i(cam.width, cam.height)

                vdiColorBuffer = vdiColor.contents
                vdiDepthBuffer = vdiDepth.contents
                gridCellsBuff = gridCells.contents

                val colorSize = windowHeight * windowWidth * maxSupersegments * 4 * 4
                val depthSize = windowWidth * windowHeight * maxSupersegments * 4 * 2
                val accelSize = (windowWidth / 8) * (windowHeight / 8) * maxSupersegments * 4

                if (vdiColorBuffer!!.remaining() != colorSize || vdiDepthBuffer!!.remaining() != depthSize || gridCellsBuff!!.remaining() != accelSize) {
                    logger.warn("Skipping transmission this frame due to inconsistency in buffer size")
                } else {

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
                            MemoryUtil.memAlloc(compressor.returnCompressBound(depthSize.toLong(), compressionTool))
                    }

                    val compressedDepthLength =
                        compressor.compress(compressedDepth!!, vdiDepthBuffer!!, 3, compressionTool)
                    compressedDepth!!.limit(compressedDepthLength.toInt())
                    vdiData.bufferSizes.depthSize = compressedDepthLength

                    val metadataOut = ByteArrayOutputStream()
                    VDIDataIO.write(vdiData, metadataOut)

                    val metadataBytes = metadataOut.toByteArray()
                    logger.info("Size of VDI data is: ${metadataBytes.size}")

                    val vdiDataSize = metadataBytes.size.toString().toByteArray(Charsets.US_ASCII)

                    var messageLength = vdiDataSize.size + metadataBytes.size + compressedColor!!.remaining()
                    messageLength += compressedDepth!!.remaining()
                    messageLength += accelSize as Int

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

    fun updateTextures(color: ByteBuffer, depth: ByteBuffer, accelGridBuffer: ByteBuffer, vdiData: VDIData,
                       firstVDI: Boolean, vdiNode: VDINode, windowWidth: Int, windowHeight: Int, numSupersegments: Int) {

        vdiNode.updateMetadata(vdiData)

        val colorTexture = UpdatableTexture(Vector3i(numSupersegments, windowHeight, windowWidth), 4, contents = null, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val colorUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, numSupersegments, windowHeight, windowWidth),
            color.slice()
        )
        colorTexture.addUpdate(colorUpdate)


        val depthTexture = UpdatableTexture(Vector3i(2 * numSupersegments, windowHeight, windowWidth), channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val depthUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, 2 * numSupersegments, windowHeight, windowWidth),
            depth.slice()
        )
        depthTexture.addUpdate(depthUpdate)

        val numGridCells = Vector3f(vdiData.metadata.windowDimensions.x.toFloat() / 8f, vdiData.metadata.windowDimensions.y.toFloat() / 8f, numSupersegments.toFloat())

        val accelTexture = UpdatableTexture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = UnsignedIntType(), mipmap = false, normalized = true, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val accelUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, windowWidth / 8, windowHeight / 8, numSupersegments),
            accelGridBuffer
        )
        accelTexture.addUpdate(accelUpdate)

        if (firstVDI || vdiNode.useSecondBuffer) { //if this is the first VDI or the second buffer was being used so far
            vdiNode.ViewOriginal = vdiData.metadata.view
            vdiNode.invViewOriginal = Matrix4f(vdiData.metadata.view).invert()

            vdiNode.material().textures["InputVDI"] = colorTexture
            vdiNode.material().textures["DepthVDI"] = depthTexture
            vdiNode.material().textures["OctreeCells"] = accelTexture

            logger.info("Uploading data for buffer 1")
        } else {
            vdiNode.ViewOriginal2 = vdiData.metadata.view
            vdiNode.invViewOriginal2 = Matrix4f(vdiData.metadata.view).invert()

            vdiNode.material().textures["InputVDI2"] = colorTexture
            vdiNode.material().textures["DepthVDI2"] = depthTexture
            vdiNode.material().textures["OctreeCells2"] = accelTexture

            logger.info("Uploading data for buffer 2")
        }

        while (!colorTexture.availableOnGPU() || !depthTexture.availableOnGPU()) {
            logger.debug("Waiting for texture transfer. color: ${colorTexture.availableOnGPU()} and depth: ${depthTexture.availableOnGPU()}")
            Thread.sleep(10)
        }

        logger.debug("Data has been detected to be uploaded to GPU")

        if (!firstVDI) {
            vdiNode.useSecondBuffer = !vdiNode.useSecondBuffer
        }
    }

    private fun decompressVDI(payload: ByteArray,
                              compressedColor: ByteBuffer,
                              compressedDepth: ByteBuffer,
                              accelGridBuffer: ByteBuffer,
                              colorBuffer: ByteBuffer,
                              depthBuffer: ByteBuffer,
                              colorSize: Int,
                              depthSize: Int,
                              compressor: DataCompressor,
                              compressionTool: DataCompressor.CompressionTool): VDIData {

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
            val decompressedColorLength = compressor.decompress(colorBuffer, compressedColor.slice(), compressionTool)
            compressedColor.limit(compressedColor.capacity())
            if (decompressedColorLength.toInt() != colorSize) {
                logger.warn("Error decompressing color message. Decompressed length: $decompressedColorLength and desired size: $colorSize")
            }
            colorDone.incrementAndGet()
        }

        compressedDepth.limit(compressedDepthLength.toInt())
        val decompressedDepthLength = compressor.decompress(depthBuffer, compressedDepth.slice(), compressionTool)
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

    fun receiveAndUpdateVDI(vdiNode: VDINode, ipAddress: String, renderer: Renderer, windowWidth: Int, windowHeight: Int, numSupersegments: Int) {
        val subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
        subscriber.isConflate = true
        val address = ipAddress
        try {
            subscriber.connect(address)
        } catch (e: ZMQException) {
            logger.warn("ZMQ Binding failed.")
        }
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)

        //Attaching initial empty textures to the second buffer so that it is not null
        vdiNode.attachEmptyTextures(VDINode.DoubleBuffer.Second)

        val compressor = DataCompressor()
        val compressionTool = DataCompressor.CompressionTool.LZ4

        //the expected sizes of each buffer
        val colorSize = windowWidth * windowHeight * numSupersegments * 4 * 4
        val depthSize = windowWidth * windowHeight * numSupersegments * 2 * 4
        val accelSize = (windowWidth/8) * (windowHeight/8) * numSupersegments * 4

        val decompressionBuffer = 1024

        val colorBuffer = MemoryUtil.memCalloc(colorSize + decompressionBuffer)
        val depthBuffer = MemoryUtil.memCalloc(depthSize + decompressionBuffer)

        val compressedColor: ByteBuffer =
            MemoryUtil.memAlloc(compressor.returnCompressBound(colorSize.toLong(), compressionTool))
        val compressedDepth: ByteBuffer =
            MemoryUtil.memAlloc(compressor.returnCompressBound(depthSize.toLong(), compressionTool))
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
                vdiData = decompressVDI(
                    payload,
                    compressedColor,
                    compressedDepth,
                    accelGridBuffer,
                    colorBuffer,
                    depthBuffer,
                    colorSize,
                    depthSize,
                    compressor,
                    compressionTool
                )

                colorBuffer.limit(colorBuffer.remaining() - decompressionBuffer)
                depthBuffer.limit(depthBuffer.remaining() - decompressionBuffer)

                updateTextures(colorBuffer.slice(), depthBuffer.slice(), accelGridBuffer, vdiData, firstVDIReceived, vdiNode, windowWidth, windowHeight, numSupersegments)

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
