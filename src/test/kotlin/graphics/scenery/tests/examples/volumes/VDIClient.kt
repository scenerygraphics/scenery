package graphics.scenery.tests.examples.volumes

import graphics.scenery.SceneryBase
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
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDIDataIO
import graphics.scenery.volumes.vdi.VDINode
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.system.measureNanoTime

class VDIClient : SceneryBase("VDI Rendering", 512, 512, wantREPL = false) {

    var hmd: TrackedStereoGlasses? = null

    val compute = VDINode()
    val plane = FullscreenObject()

    val context = ZContext(4)

    val numSupersegments = 20
    val skipEmpty = true
    val vdiStreaming = true
    var startPrinting = false

    val dataset = "Simulation"

    var newVDI = false
    val recordVideo = false
    var firstVDI = true




    val cam: Camera = DetachedHeadCamera(hmd)

    private val vulkanProjectionFix =
        Matrix4f(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, -1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.5f, 0.0f,
            0.0f, 0.0f, 0.5f, 1.0f)

    fun Matrix4f.applyVulkanCoordinateSystem(): Matrix4f {
        val m = Matrix4f(vulkanProjectionFix)
        m.mul(this)

        return m
    }

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        //Step1: create Camera
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.5f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            scene.addChild(this)
        }
        cam.farPlaneDistance = 20.0f

        val opBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)

        //Step 2: Create vdi node and it's propertie
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

        //Step3: set plane properties
        scene.addChild(plane)
        plane.material().textures["diffuse"] = compute.material().textures["OutputViewport"]!!

        //Step 4: calling record movie function
        if (recordVideo) {
//          settings.set("VideoEncoder.Format", "HEVC")
            settings.set("VideoEncoder.Bitrate", 20000000)
            renderer?.recordMovie("${dataset}VDIRendering.mp4")

            thread {
                Thread.sleep(56000)
                renderer?.recordMovie()
            }
        }

        //Step 5: call receive and update VDI
        thread {
            receiveAndUpdateVDI(compute)
        }

        //Step 6: create connection
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

            if (!((rotArray.contentEquals(prevRot)) && (posArray.contentEquals(prevPos)))) {
                list.add(rotArray)
                list.add(posArray)

                val bytes = objectMapper.writeValueAsBytes(list)

                publisher.send(bytes)

                prevPos = posArray
                prevRot = rotArray
            }

            compute.printData = startPrinting
            startPrinting = false
        }

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

        if (firstVDI || compute.useSecondBuffer) { //if this is the first VDI or the second buffer was being used so far
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

        while (!colorTexture.availableOnGPU() || !depthTexture.availableOnGPU()) {
            logger.debug("Waiting for texture transfer. color: ${colorTexture.availableOnGPU()} and depth: ${depthTexture.availableOnGPU()}")
            Thread.sleep(10)
        }

        logger.debug("Data has been detected to be uploaded to GPU")
        newVDI = true

        if (!firstVDI) {
            compute.useSecondBuffer = !compute.useSecondBuffer
        }
    }

    private fun receiveAndUpdateVDI(compute: VDINode) {

        while (!renderer!!.firstImageReady) {
            Thread.sleep(100)
        }

        var subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
        subscriber.setConflate(true)
        val address = "tcp://localhost:6655"
//        val address = "tcp://172.24.150.81:6655"
//        val address = "tcp://10.1.224.71:6655"
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

        compute.visible = true

        //upload one texture to buffer 2
        val numGridCells = Vector3f(windowWidth.toFloat() / 8f, windowHeight.toFloat() / 8f, numSupersegments.toFloat())

        val initColor = MemoryUtil.memAlloc(numSupersegments * windowHeight * windowWidth * 4 * 4)
        val initDepth = MemoryUtil.memAlloc(2 * numSupersegments * windowHeight * windowWidth * 4)
        val initAccel = MemoryUtil.memAlloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)

        val initialColorTexture = UpdatableTexture(Vector3i(numSupersegments, windowHeight, windowWidth), 4, contents = null, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        val initialcolorUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, numSupersegments, windowHeight, windowWidth),
            initColor
        )
        initialColorTexture.addUpdate(initialcolorUpdate)


        val initialDepthTexture = UpdatableTexture(Vector3i(2*numSupersegments, windowHeight, windowWidth),  channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        val initialDepthUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, 2 * numSupersegments, windowHeight, windowWidth),
            initDepth
        )
        initialDepthTexture.addUpdate(initialDepthUpdate)

        val initialAccelTexture = UpdatableTexture(Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()),  channels = 1, contents = null, usageType = hashSetOf(
            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture, Texture.UsageType.AsyncLoad), type = UnsignedIntType(), mipmap = false, normalized = true, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        val initalAccelUpdate = UpdatableTexture.TextureUpdate(
            UpdatableTexture.TextureExtents(0, 0, 0, windowWidth / 8, windowHeight / 8, numSupersegments),
            initAccel
        )
        initialAccelTexture.addUpdate(initalAccelUpdate)

        compute.material().textures["InputVDI2"] = initialColorTexture
        compute.material().textures["DepthVDI2"] = initialDepthTexture
        compute.material().textures["OctreeCells2"] = initialAccelTexture

        while(!initialColorTexture.availableOnGPU() || !initialDepthTexture.availableOnGPU() ||     !initialAccelTexture.availableOnGPU()) {
            logger.debug("Waiting for texture transfer.")
            Thread.sleep(10)
        }
        logger.info("done with initial textures")

        val buff: ByteArray
        val depthBuff: ByteArray
        val octBuff: ByteArray
        var colBuffer: ByteBuffer? = null
        var depthBuffer: ByteBuffer? = null
        var accelGridBuff: ByteBuffer? = null

        val basePath = "/home/aryaman/Repositories/scenery-insitu/"
//        val basePath = "e:/vdis/Simulation/"

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

            depthBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 2 * 2 * 2)
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
                }
                logger.info("Time taken for the receive: ${receiveTime/1e9}")

                if (payload != null) {
                    val metadataSize = payload.sliceArray(0 until 3).toString(Charsets.US_ASCII).toInt() //hardcoded 3 digit number
                    val metadata = ByteArrayInputStream(payload.sliceArray(3 until (metadataSize + 3)))
                    vdiData = VDIDataIO.read(metadata)
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
                        val decompressedColorLength = compressor.decompress(color, compressedColor.slice(), compressionTool)
                        compressedColor.limit(compressedColor.capacity())
                        if (decompressedColorLength.toInt() != colorSize) {
                            logger.warn("Error decompressing color message. Decompressed length: $decompressedColorLength and desired size: $colorSize")
                        }
                        colorDone.incrementAndGet()
                    }

                    compressedDepth.limit(compressedDepthLength.toInt())
                    val decompressedDepthLength = compressor.decompress(depth, compressedDepth.slice(), compressionTool)
                    compressedDepth.limit(compressedDepth.capacity())
                    if (decompressedDepthLength.toInt() != depthSize) {
                        logger.warn("Error decompressing depth message. Decompressed length: $decompressedDepthLength and desired size: $depthSize")
                    }

                    while (colorDone.get() == 0) {
                        Thread.sleep(20)
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
        }
    }
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDIClient().main()
        }
    }
}