package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.textures.Texture
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.ui.SwingBridgeFrame
import graphics.scenery.utils.DataCompressor
import graphics.scenery.utils.Image
import graphics.scenery.utils.VideoDecoder
import graphics.scenery.volumes.DummyVolume
import graphics.scenery.volumes.EmptyNode
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.TransferFunctionEditor
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDIDataIO
import graphics.scenery.volumes.vdi.VDINode
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.system.measureNanoTime

class ClientApplication : SceneryBase("Client Application", 512, 512)  {

    var hmd: TrackedStereoGlasses? = null
    var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)
    val context = ZContext(4)

    val numSupersegments = 20
    val skipEmpty = true
    var vdiStreaming = true
    var newVDI = false
    var firstVDI = true
    var firstVDIStream = true
    var firstVR = true
    var currentlyVolumeRendering = false
    var videoDecoding = true

    val compute = VDINode()
    val switch = EmptyNode()

    val vulkanProjectionFix =
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

        //Step 1: Create necessary common components
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            name = "ClientCamera"
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)
            scene.addChild(this)
        }

        //Step 2: Create necessary video-streaming  components
        val dummyVolume = DummyVolume()
        with(dummyVolume) {
            name = "DummyVolume"
            transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
            scene.addChild(this)
        }

        val VRPlane = FullscreenObject()
        with(VRPlane){
            name = "VRplane"
            VRPlane.wantsSync = false
        }

        val bridge = SwingBridgeFrame("TransferFunctionEditor")
        val tfUI = TransferFunctionEditor(dummyVolume, bridge)
        tfUI.name = dummyVolume.name
        val swingUiNode = tfUI.mainFrame.uiNode
        swingUiNode.spatial() {
            position = Vector3f(2f,0f,0f)
        }

        //Step 3: Create necessary vdi-streaming  components
        val opBuffer = MemoryUtil.memCalloc(windowWidth * windowHeight * 4)
        compute.name = "vdi node"
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("VDIRenderer.comp"), this@ClientApplication::class.java)))
        compute.material().textures["OutputViewport"] = Texture.fromImage(Image(opBuffer, windowWidth, windowHeight),
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth, windowHeight, 1),
            invocationType = InvocationType.Permanent
        )
        compute.visible = true

        val VDIPlane = FullscreenObject()
        with(VDIPlane){
            name = "VDIplane"
            material().textures["diffuse"] = compute.material().textures["OutputViewport"]!!
        }

        //Step 4: add Empty Node to scene
        scene.addChild(switch)
        var count = 0
        switch.value = "toVR"

        //Step 5: switching code
        thread {
            while (true){
                count++
                if(count%100000000000 == 0.toLong()){
                    count = 0
                    logger.warn("$count") }

                if (tfUI.switchTo != "")
                    switch.value = tfUI.switchTo

                if (!currentlyVolumeRendering && switch.value.equals("toVR")){
                    logger.warn("Volume Rendering")

                    vdiStreaming = false
                    scene.addChild(VRPlane)
                    scene.removeChild(compute)
                    scene.removeChild(VDIPlane)
                    videoDecoding = true

                    thread {
                        decodeVideo(VRPlane)
                    }

                    currentlyVolumeRendering = true

                }
               else if (currentlyVolumeRendering && switch.value.equals("toVDI")){
                    logger.warn("VDI streaming")

                    videoDecoding = false
                    scene.addChild(VDIPlane)
                    scene.addChild(compute)
                    scene.removeChild(VRPlane)
                    cam.farPlaneDistance = 20.0f
                    vdiStreaming = true

                    if (firstVDIStream){
                       thread {
//                           receiveAndUpdateVDI(compute)
                       }
                       firstVDIStream = false
                    }

                    currentlyVolumeRendering = false

                }
            }
        }
    }
    private fun decodeVideo( plane: FullscreenObject){
        var decodedFrameCount: Int = 0
        val videoDecoder = VideoDecoder("scenery-stream.sdp")
        logger.info("video decoder object created")

        while (!sceneInitialized()) {
            Thread.sleep(200)
        }

        decodedFrameCount = 1
        logger.info("Decoding and displaying frames")
        while (videoDecoding && videoDecoder.nextFrameExists) {
            val image = videoDecoder.decodeFrame()
            if(image != null) { // image can be null, e.g. when the decoder encounters invalid information between frames
                drawFrame(image, videoDecoder.videoWidth, videoDecoder.videoHeight, plane)
                decodedFrameCount++
            }
        }
        decodedFrameCount -= 1
        videoDecoder.close()
        logger.info("Done decoding and displaying $decodedFrameCount frames.")
    }
    private fun drawFrame(tex: ByteArray, width: Int, height: Int, plane: FullscreenObject) {
        if(buffer.capacity() == 0) {
            buffer = BufferUtils.allocateByteAndPut(tex)
        } else {
            buffer.put(tex).flip()
        }
        plane.material {
            textures["diffuse"] = Texture(Vector3i(width, height, 1), 4, contents = buffer, mipmap = true)
        }
    }

    fun updateTextures(color: ByteBuffer, depth: ByteBuffer, accelGridBuffer: ByteBuffer, vdiData: VDIData, firstVDI: Boolean) {

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

        val basePath = "/home/salhi/Repositories/scenery-insitu/"

//        if(!vdiStreaming) {
//            val vdiParams = "_${windowWidth}_${windowHeight}_${numSupersegments}_0_"
//
//            val file = FileInputStream(File(basePath + "vdi${vdiParams}dump4"))
//
//            vdiData = VDIDataIO.read(file)
//
//            //preparing the files for loading from disk
//            buff = File(basePath + "VDI${vdiParams}4_ndc_col").readBytes()
//            depthBuff = File(basePath + "VDI${vdiParams}4_ndc_depth").readBytes()
//            octBuff = File(basePath + "VDI${vdiParams}4_ndc_octree").readBytes()
//
//            colBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 4 * 4)
//            colBuffer.put(buff).flip()
//            colBuffer.limit(colBuffer.capacity())
//
//            depthBuffer = MemoryUtil.memCalloc(windowHeight * windowWidth * numSupersegments * 2 * 2 * 2)
//            depthBuffer.put(depthBuff).flip()
//            depthBuffer.limit(depthBuffer.capacity())
//
//            val numGridCells = Vector3f(vdiData.metadata.windowDimensions.x.toFloat() / 8f, vdiData.metadata.windowDimensions.y.toFloat() / 8f, numSupersegments.toFloat())
//            accelGridBuff = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)
//            if(skipEmpty) {
//                accelGridBuff.put(octBuff).flip()
//            }
//            compute.skip_empty = skipEmpty
//        }

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

                    updateTextures(color.slice(), depth.slice(), accelGridBuffer, vdiData, firstVDI)

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
                updateTextures(colBuffer!!, depthBuffer!!, accelGridBuff!!, vdiData, firstVDI)
                firstVDI = false
                compute.visible = true
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ClientApplication().main()
        }
    }
}


