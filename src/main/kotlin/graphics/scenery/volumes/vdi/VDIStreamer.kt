package graphics.scenery.volumes.vdi

import graphics.scenery.Camera
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.utils.DataCompressor
import graphics.scenery.utils.lazyLogger
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.VolumeManager
import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

class VDIStreamer {

    private val logger by lazyLogger()

    /** param to determine the state of vdi streaming */
    var vdiStreaming: Boolean = true

    /** the number of VDIs streamed so far */
    var vdisStreamed: Int = 0

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
                }

                if (compressedColor == null) {
                    compressedColor =
                        MemoryUtil.memAlloc(compressor.returnCompressBound(colorSize.toLong(), compressionTool))
                }

                val compressedColorLength = compressor.compress(compressedColor!!, vdiColorBuffer!!, 3, compressionTool)
                compressedColor!!.limit(compressedColorLength.toInt())
                vdiData.bufferSizes.colorSize = compressedColorLength

                if (compressedDepth == null) {
                    compressedDepth =
                        MemoryUtil.memAlloc(compressor.returnCompressBound(depthSize.toLong(), compressionTool))
                }

                val compressedDepthLength = compressor.compress(compressedDepth!!, vdiDepthBuffer!!, 3, compressionTool)
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

                compressedColor!!.slice().get(message, vdiDataSize.size + metadataBytes.size, compressedColor!!.remaining())
                compressedDepth!!.slice().get(message,vdiDataSize.size + metadataBytes.size + compressedColor!!.remaining(), compressedDepth!!.remaining())

                vdiData.bufferSizes.accelGridSize = accelSize.toLong()

                gridCellsBuff!!.get(message, vdiDataSize.size + metadataBytes.size + compressedColor!!.remaining() +
                        compressedDepth!!.remaining(), gridCellsBuff!!.remaining())
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
            firstFrame = false
        }
    }

}
