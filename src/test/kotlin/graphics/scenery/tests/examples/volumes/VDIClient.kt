package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.vdi.VDICompressor
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDIDataIO
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.concurrent.thread
import kotlin.system.measureNanoTime

/**
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */


class VDIClient : SceneryBase("VDI Rendering", 1280, 720, wantREPL = false) {
    var hmd: TrackedStereoGlasses? = null

    val compute = CustomNode()

    val numSupersegments = 30
    val skipEmpty = false

    val subsampling = false
    var desiredFrameRate = 30
    var maxFrameRate = 90

    private val vulkanProjectionFix =
        Matrix4f(
            1.0f,  0.0f, 0.0f, 0.0f,
            0.0f, -1.0f, 0.0f, 0.0f,
            0.0f,  0.0f, 0.5f, 0.0f,
            0.0f,  0.0f, 0.5f, 1.0f)

    fun Matrix4f.applyVulkanCoordinateSystem(): Matrix4f {
        val m = Matrix4f(vulkanProjectionFix)
        m.mul(this)

        return m
    }

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera(hmd)

        with(cam) {
            spatial().position = Vector3f(-4.365f, 0.38f, 0.62f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        cam.spatial {
            position = Vector3f(4.622E+0f, -9.060E-1f, -1.047E+0f) //V1 for kingsnake
            rotation = Quaternionf(5.288E-2, -9.096E-1, -1.222E-1, 3.936E-1)

//            position = Vector3f(-2.607E+0f, -5.973E-1f,  2.415E+0f) // V1 for Beechnut
//            rotation = Quaternionf(-9.418E-2, -7.363E-1, -1.048E-1, -6.618E-1)
//
//            position = Vector3f( 1.897E+0f, -5.994E-1f, -1.899E+0f) //V1 for Boneplug
//            rotation = Quaternionf( 5.867E-5,  9.998E-1,  1.919E-2,  4.404E-3)
        }

        cam.farPlaneDistance = 20.0f

        val opBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)

        compute.name = "vdi node"
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("AmanatidesJumps.comp"), this@VDIClient::class.java)))
        compute.material().textures["OutputViewport"] = Texture.fromImage(Image(opBuffer, windowWidth, windowHeight),
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )

        compute.visible = false

        scene.addChild(compute)

        val plane = FullscreenObject()
        scene.addChild(plane)
        plane.material().textures["diffuse"] = compute.material().textures["OutputViewport"]!!

        thread {
            receiveAndUpdateVDI(compute)
        }

    }

    private fun receiveAndUpdateVDI(compute: CustomNode) {
        val context = ZContext(1)
        var subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
        subscriber.setConflate(true)
//        val address = "tcp://localhost:6655"
        val address = "tcp://10.1.224.71:6655"
        try {
            subscriber.connect(address)
        } catch (e: ZMQException) {
            logger.warn("ZMQ Binding failed.")
        }
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)

        var vdiData = VDIData()

        val compressor = VDICompressor()
        val compressionTool = VDICompressor.CompressionTool.LZ4

        val colorSize = windowWidth * windowHeight * numSupersegments * 4 * 4
        val depthSize = windowWidth * windowHeight * numSupersegments * 2 * 4

        val decompressionBuffer = 1024

        val color = MemoryUtil.memCalloc(colorSize + decompressionBuffer)
        val depth = MemoryUtil.memCalloc(depthSize + decompressionBuffer)

        val compressedColor: ByteBuffer =
            MemoryUtil.memAlloc(compressor.returnCompressBound(colorSize.toLong(), compressionTool))
        val compressedDepth: ByteBuffer =
            MemoryUtil.memAlloc(compressor.returnCompressBound(depthSize.toLong(), compressionTool))


        while(true) {

            val payload: ByteArray?

            val receiveTime = measureNanoTime {
                payload = subscriber.recv()

                logger.info("Received payload of size ${payload.size}. Sum is: ${payload.sum()}")
            }

            logger.info("Time taken for the receive: ${receiveTime/1e9}")


            if (payload != null) {
                val metadataSize = payload.sliceArray(0 until 3).toString(Charsets.US_ASCII).toInt() //hardcoded 3 digit number

                logger.info("vdi data size is: $metadataSize")

                val metadata = ByteArrayInputStream(payload.sliceArray(3 until (metadataSize + 3)))
                vdiData = VDIDataIO.read(metadata)
                logger.info("Received metadata has nw: ${vdiData.metadata.nw}")
                logger.info("Index of received VDI: ${vdiData.metadata.index}")

                val compressedColorLength = vdiData.bufferSizes.colorSize
                val compressedDepthLength = vdiData.bufferSizes.depthSize

                compressedColor.put(payload.sliceArray((metadataSize + 3) until (metadataSize + 3 + compressedColorLength.toInt())))
                compressedColor.flip()
                compressedDepth.put(payload.sliceArray((metadataSize + 3) + compressedColorLength.toInt() until payload.size))
                compressedDepth.flip()

                compressedColor.limit(compressedColorLength.toInt())
                val decompressedColorLength = compressor.decompress(color, compressedColor.slice(), compressionTool)
                compressedColor.limit(compressedColor.capacity())
                if (decompressedColorLength.toInt() != colorSize) {
                    logger.warn("Error decompressing color message. Decompressed length: $decompressedColorLength and desired size: $colorSize")
                }

                compressedDepth.limit(compressedDepthLength.toInt())
                val decompressedDepthLength = compressor.decompress(depth, compressedDepth.slice(), compressionTool)
                compressedDepth.limit(compressedDepth.capacity())
                if (decompressedDepthLength.toInt() != depthSize) {
                    logger.warn("Error decompressing depth message. Decompressed length: $decompressedDepthLength and desired size: $depthSize")
                }

                color.limit(color.remaining() - decompressionBuffer)
                compute.material().textures["InputVDI"] = Texture(Vector3i(numSupersegments, windowHeight, windowWidth), 4, contents = color.slice(), usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
                    type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                color.limit(color.capacity())

                depth.limit(depth.remaining() - decompressionBuffer)
                compute.material().textures["DepthVDI"] = Texture(Vector3i(2*numSupersegments, windowHeight, windowWidth),  channels = 1, contents = depth.slice(), usageType = hashSetOf(
                    Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                depth.limit(depth.capacity())

                compute.ProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()
                compute.invProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem().invert()
                compute.ViewOriginal = vdiData.metadata.view
                compute.nw = vdiData.metadata.nw
                compute.invViewOriginal = Matrix4f(vdiData.metadata.view).invert()
                compute.invModel = Matrix4f(vdiData.metadata.model).invert()
                compute.volumeDims = vdiData.metadata.volumeDimensions
                compute.do_subsample = false

                compute.visible = true

            } else {
                logger.info("Payload received but is null")
            }


            logger.info("Received and updated VDI data")
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIClient().main()
        }
    }
}
