package graphics.scenery.tests.examples.volumes.vdi

import com.fasterxml.jackson.databind.ObjectMapper
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.textures.Texture
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.utils.DataCompressor
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDIDataIO
import graphics.scenery.volumes.vdi.VDINode
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.scijava.ui.behaviour.ClickBehaviour
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.system.measureNanoTime

/**
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */


class VDIClient : SceneryBase("VDI Rendering", 400, 400, wantREPL = false) {
    var hmd: TrackedStereoGlasses? = null

    val compute = VDINode()

    val context = ZContext(4)

    val numSupersegments = 20
    val skipEmpty = true
    val vdiStreaming = false

    val subsampling = false
    var desiredFrameRate = 30
    var maxFrameRate = 90

    var startPrinting = false
    var sendCamera = false

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

//            position = Vector3f(2.041E-1f, -5.253E+0f, -1.321E+0f) //V1 for Simulation
//            rotation = Quaternionf(9.134E-2, -9.009E-1,  3.558E-1, -2.313E-1)


//            position = Vector3f(-2.607E+0f, -5.973E-1f,  2.415E+0f) // V1 for Beechnut
//            rotation = Quaternionf(-9.418E-2, -7.363E-1, -1.048E-1, -6.618E-1)
//
//            position = Vector3f( 1.897E+0f, -5.994E-1f, -1.899E+0f) //V1 for Boneplug
//            rotation = Quaternionf( 5.867E-5,  9.998E-1,  1.919E-2,  4.404E-3)
        }

        cam.farPlaneDistance = 20.0f

        val opBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)

        compute.name = "vdi node"
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("VDIRenderer.comp"), this@VDIClient::class.java)))
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

        val objectMapper = ObjectMapper(MessagePackFactory())

        var publisher: ZMQ.Socket = context.createSocket(SocketType.PUB)
        publisher.isConflate = true

        val address: String = "tcp://0.0.0.0:6655"
        try {
            publisher.bind(address)
        } catch (e: ZMQException) {
            logger.warn("Binding failed, trying random port: $e")
        }

        var prevPos = floatArrayOf(0f, 0f, 0f)
        var prevRot = floatArrayOf(0f, 0f, 0f, 0f)

        (renderer as VulkanRenderer).postRenderLambdas.add {
            val list: MutableList<Any> = ArrayList()
            val rotArray = floatArrayOf(cam.spatial().rotation.x, cam.spatial().rotation.y, cam.spatial().rotation.z, cam.spatial().rotation.w)
            val posArray = floatArrayOf(cam.spatial().position.x(), cam.spatial().position.y(), cam.spatial().position.z())

            if(!((rotArray.contentEquals(prevRot)) && (posArray.contentEquals(prevPos))) && sendCamera) {
                list.add(rotArray)
                list.add(posArray)

                val bytes = objectMapper.writeValueAsBytes(list)

                logger.info("Sent camera details\n position: ${cam.spatial().position}\nrotation: ${cam.spatial().rotation}")

                publisher.send(bytes)

                prevPos = posArray
                prevRot = rotArray

//                sendCamera = false
            }


            compute.printData = startPrinting
            startPrinting = false
        }

        thread {
            while (true) {
                Thread.sleep(2000)
                logger.info("cam pos: ${cam.spatial().position}")
                logger.info("cam rot: ${cam.spatial().rotation}")
            }
        }
    }

    fun rotateCamera(degrees: Float, pitch: Boolean = false) {
        val camTarget = Vector3f(1.920E+0f, -1.920E+0f,  1.491E+0f)

        val cam = scene.findObserver()!!
        cam.targeted = true
        val frameYaw: Float
        val framePitch: Float

        if(pitch) {
            framePitch = degrees / 180.0f * Math.PI.toFloat()
            frameYaw = 0f
        } else {
            frameYaw = degrees / 180.0f * Math.PI.toFloat()
            framePitch = 0f
        }

        // first calculate the total rotation quaternion to be applied to the camera
        val yawQ = Quaternionf().rotateXYZ(0.0f, frameYaw, 0.0f).normalize()
        val pitchQ = Quaternionf().rotateXYZ(framePitch, 0.0f, 0.0f).normalize()

//        logger.info("cam target: ${camTarget}")

        val distance = (camTarget - cam.spatial().position).length()
        cam.spatial().rotation = pitchQ.mul(cam.spatial().rotation).mul(yawQ).normalize()
        cam.spatial().position = camTarget + cam.forward * distance * (-1.0f)
//        logger.info("new camera pos: ${cam.spatial().position}")
//        logger.info("new camera rotation: ${cam.spatial().rotation}")
//        logger.info("camera forward: ${cam.forward}")
    }

    fun updateTextures(color: ByteBuffer, depth: ByteBuffer, accelGridBuffer: ByteBuffer, vdiData: VDIData, accelSize: Int, firstVDI: Boolean) {

        compute.ProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem()
        compute.invProjectionOriginal = Matrix4f(vdiData.metadata.projection).applyVulkanCoordinateSystem().invert()
        compute.nw = vdiData.metadata.nw
        compute.invModel = Matrix4f(vdiData.metadata.model).invert()
        compute.volumeDims = vdiData.metadata.volumeDimensions
        compute.do_subsample = false
        compute.vdiWidth = vdiData.metadata.windowDimensions.x
        compute.vdiHeight = vdiData.metadata.windowDimensions.y

        val colorTexture = UpdatableTexture(Vector3i(numSupersegments, windowHeight, windowWidth), 4, contents = null, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val colorUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, numSupersegments, windowHeight, windowWidth),
            color.slice()
        )
        colorTexture.addUpdate(colorUpdate)


        val depthTexture = UpdatableTexture(Vector3i(2*numSupersegments, windowHeight, windowWidth),  channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val depthUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, 2 * numSupersegments, windowHeight, windowWidth),
            depth.slice()
        )
        depthTexture.addUpdate(depthUpdate)

        val numGridCells = Vector3f(vdiData.metadata.windowDimensions.x.toFloat() / 8f, vdiData.metadata.windowDimensions.y.toFloat() / 8f, numSupersegments.toFloat())

        val accelTexture = UpdatableTexture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()),  channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = UnsignedIntType(), mipmap = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val accelUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, windowWidth / 8, windowHeight / 8, numSupersegments),
            accelGridBuffer
        )
        accelTexture.addUpdate(accelUpdate)

        logger.info("Before assignment, color mutex is: ${colorTexture.gpuMutex.availablePermits()} and depth: ${depthTexture.gpuMutex.availablePermits()}")
        logger.info("Before assignment, color mutex is: ${colorTexture.mutex.availablePermits()} and depth: ${depthTexture.mutex.availablePermits()}")

        if(firstVDI || compute.useSecondBuffer) { //if this is the first VDI or the second buffer was being used so far
            compute.ViewOriginal = vdiData.metadata.view
            compute.invViewOriginal = Matrix4f(vdiData.metadata.view).invert()

            compute.material().textures["InputVDI"] = colorTexture
            compute.material().textures["DepthVDI"] = depthTexture
            compute.material().textures["OctreeCells"] = accelTexture

            logger.info("Uploading data for buffer 1")
        } else {
            compute.ViewOriginal2 = vdiData.metadata.view
            compute.invViewOriginal2 = Matrix4f(vdiData.metadata.view).invert()

            compute.material().textures["InputVDI2"] = colorTexture
            compute.material().textures["DepthVDI2"] = depthTexture
            compute.material().textures["OctreeCells2"] = accelTexture

            logger.info("Uploading data for buffer 2")
        }

//                logger.info("color mutex is: ${colorTexture.gpuMutex.availablePermits()} and depth: ${depthTexture.gpuMutex.availablePermits()}")

        while(!colorTexture.availableOnGPU() || !depthTexture.availableOnGPU()) {
            logger.info("Waiting for texture transfer. color: ${colorTexture.availableOnGPU()} and depth: ${depthTexture.availableOnGPU()}")
            Thread.sleep(1000)
        }

        logger.warn("Data has been detected to be uploaded to GPU")

        if(!firstVDI) {
            compute.useSecondBuffer = !compute.useSecondBuffer
        }

    }

    private fun receiveAndUpdateVDI(compute: VDINode) {
        var subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
        subscriber.setConflate(true)
//        val address = "tcp://localhost:6655"
//        val address = "tcp://172.24.150.81:6655"
        val address = "tcp://10.1.224.71:6655"
        try {
            subscriber.connect(address)
        } catch (e: ZMQException) {
            logger.warn("ZMQ Binding failed.")
        }
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)

        var vdiData = VDIData()

        val compressor = DataCompressor()
        val compressionTool = DataCompressor.CompressionTool.LZ4

        val colorSize = windowWidth * windowHeight * numSupersegments * 4 * 4
        val depthSize = windowWidth * windowHeight * numSupersegments * 2 * 4
        val accelSize = (windowWidth/8) * (windowHeight/8) * numSupersegments * 4

        val decompressionBuffer = 1024

        val color = MemoryUtil.memCalloc(colorSize + decompressionBuffer)
        val depth = MemoryUtil.memCalloc(depthSize + decompressionBuffer)

        val compressedColor: ByteBuffer =
            MemoryUtil.memAlloc(compressor.returnCompressBound(colorSize.toLong(), compressionTool))
        val compressedDepth: ByteBuffer =
            MemoryUtil.memAlloc(compressor.returnCompressBound(depthSize.toLong(), compressionTool))
        val accelGridBuffer =
            MemoryUtil.memAlloc(accelSize)

        var firstVDI = true

        val emptyColor = MemoryUtil.memCalloc(4 * 4)
        val emptyColorTexture = Texture(Vector3i(1, 1, 1), 4, contents = emptyColor, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val emptyDepth = MemoryUtil.memCalloc(1 * 4)
        val emptyDepthTexture = Texture(Vector3i(1, 1, 1), 1, contents = emptyDepth, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = FloatType(), mipmap = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        val emptyAccel = MemoryUtil.memCalloc(4)
        val emptyAccelTexture = Texture(
            Vector3i(1, 1, 1), 1, contents = emptyAccel, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = UnsignedIntType(), mipmap = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour
        )

        compute.material().textures["InputVDI"] = emptyColorTexture
        compute.material().textures["InputVDI2"] = emptyColorTexture
        compute.material().textures["DepthVDI"] = emptyDepthTexture
        compute.material().textures["DepthVDI2"] = emptyDepthTexture
        compute.material().textures["OctreeCells"] = emptyAccelTexture
        compute.material().textures["OctreeCells2"] = emptyAccelTexture

//        val colorTexture = UpdatableTexture(
//            Vector3i(1024, 1024, 256),
//            channels = 1,
//            type = UnsignedByteType(),
//            usageType = hashSetOf(Texture.UsageType.Texture, Texture.UsageType.AsyncLoad, Texture.UsageType.LoadStoreImage),
//            contents = null
//        )
//
//        val colorUpdate = UpdatableTexture.TextureUpdate(
//            UpdatableTexture.TextureExtents(0, 0, 0, 1024, 1024, 256),
//            MemoryUtil.memAlloc(256*1024*1024)
//        )
//        colorTexture.addUpdate(colorUpdate)
//
//        compute.material().textures["InputVDI"] = colorTexture
//
//        val waitTime = measureTimeMillis {
//            // Here, we wait until the texture is marked as available on the GPU
//            while(!colorTexture.availableOnGPU()) {
//                logger.info("Texture not available yet, uploaded=${colorTexture.uploaded.get()}/permits=${colorTexture.gpuMutex.availablePermits()}")
//                Thread.sleep(10)
//            }
//        }
//
//        logger.info("Texture is available now, waited $waitTime ms")

        compute.visible = true

        val buff: ByteArray
        val depthBuff: ByteArray
        val octBuff: ByteArray
        var colBuffer: ByteBuffer? = null
        var depthBuffer: ByteBuffer? = null
        var accelGridBuff: ByteBuffer? = null

        val basePath = "/home/aryaman/Repositories/scenery-insitu/"
        val dataset = "Kingsnake"

        if(!vdiStreaming) {
            val vdiParams = "_${windowWidth}_${windowHeight}_${numSupersegments}_0_"

            val file = FileInputStream(File(basePath + "${dataset}vdi${vdiParams}dump4"))

            vdiData = VDIDataIO.read(file)

            //preparing the files for loading from disk
            buff = File(basePath + "${dataset}VDI${vdiParams}4_ndc_col").readBytes()
            depthBuff = File(basePath + "${dataset}VDI${vdiParams}4_ndc_depth").readBytes()
            octBuff = File(basePath + "${dataset}VDI${vdiParams}4_ndc_octree").readBytes()



            colBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 4 * 4)

            colBuffer.put(buff).flip()
            colBuffer.limit(colBuffer.capacity())

            depthBuffer =
                MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 2 * 2 * 2)

            depthBuffer.put(depthBuff).flip()
            depthBuffer.limit(depthBuffer.capacity())

            val numGridCells = Vector3f(vdiData.metadata.windowDimensions.x.toFloat() / 8f, vdiData.metadata.windowDimensions.y.toFloat() / 8f, numSupersegments.toFloat())
            accelGridBuff = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)
            if(skipEmpty) {
                accelGridBuff.put(octBuff).flip()
            }
            compute.skip_empty = skipEmpty
        }

        while(true) {

            if(vdiStreaming) {

                val payload: ByteArray?

                logger.info("Waiting for VDI")

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
                    compressedDepth.put(payload.sliceArray((metadataSize + 3) + compressedColorLength.toInt() until (metadataSize + 3) + compressedColorLength.toInt() + compressedDepthLength.toInt()))
                    compressedDepth.flip()

                    logger.info("bytes to be put into grid buffer: ${(payload.size - ((metadataSize + 3) + compressedColorLength.toInt() + compressedDepthLength.toInt()))}")

                    accelGridBuffer.put(payload.sliceArray((metadataSize + 3) + compressedColorLength.toInt() + compressedDepthLength.toInt() until payload.size))
                    accelGridBuffer.flip()

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
                    depth.limit(depth.remaining() - decompressionBuffer)

                    updateTextures(color.slice(), depth.slice(), accelGridBuffer, vdiData, accelSize, firstVDI)

                    color.limit(color.capacity())
                    depth.limit(depth.capacity())

                    firstVDI = false

                    compute.visible = true

                } else {
                    logger.info("Payload received but is null")
                }

                logger.info("Received and updated VDI data")
            } else {
                Thread.sleep(1000)
                updateTextures(colBuffer!!, depthBuffer!!, accelGridBuff!!, vdiData, accelSize, firstVDI)
                firstVDI = false
                compute.visible = true
            }


//            Thread.sleep(2000)
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()

        inputHandler?.addBehaviour("send_camera", ClickBehaviour { _, _ ->
            sendCamera = true
        })
        inputHandler?.addKeyBinding("send_camera", "O")

        inputHandler?.addBehaviour("print_debug", ClickBehaviour { _, _ ->
            startPrinting = true
        })
        inputHandler?.addKeyBinding("print_debug", "H")

        inputHandler?.addBehaviour("rotate_camera", ClickBehaviour { r_, _ ->
            rotateCamera(10f)
        })
        inputHandler?.addKeyBinding("rotate_camera", "R")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIClient().main()
        }
    }
}
