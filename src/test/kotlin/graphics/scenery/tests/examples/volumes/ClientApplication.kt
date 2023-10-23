package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.ui.SwingBridgeFrame
import graphics.scenery.utils.DataCompressor
import graphics.scenery.utils.VideoDecoder
import graphics.scenery.volumes.DummyVolume
import graphics.scenery.volumes.EmptyNode
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.TransferFunctionEditor
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDIDataIO
import graphics.scenery.volumes.vdi.VDINode
import graphics.scenery.volumes.vdi.VDIStreamer
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

    var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)
    val context = ZContext(4)

    val numSupersegments = 20
    var vdiStreaming = true
    var newVDI = false
    var firstVDI = true
    var firstVDIStream = true
    var currentlyVolumeRendering = false

    lateinit var vdiNode: VDINode
    val switch = EmptyNode()

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
            transferFunction = TransferFunction.ramp(0.1f, 0.5f)
            scene.addChild(this)
        }

        val videoPlane = FullscreenObject()
        with(videoPlane){
            name = "VRplane"
            videoPlane.wantsSync = false
        }

        val bridge = SwingBridgeFrame("1DTransferFunctionEditor")
        val transferFunctionUI = TransferFunctionEditor(dummyVolume)
        bridge.addPanel(transferFunctionUI)
        transferFunctionUI.name = dummyVolume.name
        val swingUiNode = bridge.uiNode
        swingUiNode.spatial() {
            position = Vector3f(2f,0f,0f)
        }

        val VDIPlane = FullscreenObject()
        with(VDIPlane){
            name = "VDIplane"
            material().textures["diffuse"] = vdiNode.material().textures["OutputViewport"]!!
        }

        //Step 4: add Empty Node to scene
        scene.addChild(switch)
        var count = 0
        switch.value = "toVR"

        val vdiStreamer = VDIStreamer()

        //Step 5: switching code
        thread {
            while (true){
                count++
                if(count%100000000000 == 0.toLong()){
                    count = 0
                    logger.info("$count")
                }

                // TODO restore
//                if (transferFunctionUI.switchTo != "")
//                    switch.value = transferFunctionUI.switchTo

                if (!currentlyVolumeRendering && switch.value.equals("toVR")){

                    logger.info("Volume Rendering")

                    vdiStreaming = false
                    scene.addChild(videoPlane)
                    scene.removeChild(vdiNode)
                    scene.removeChild(VDIPlane)

                    thread {
                        decodeVideo(videoPlane)
                    }
                    currentlyVolumeRendering = true
                }
               else if (currentlyVolumeRendering && switch.value.equals("toVDI")){

                   logger.info("VDI streaming")

                    vdiStreaming = true
                    scene.addChild(VDIPlane)
                    scene.addChild(vdiNode)
                    scene.removeChild(videoPlane)
                    cam.farPlaneDistance = 20.0f

                    if (firstVDIStream){
                       thread {
                           vdiStreamer.receiveAndUpdateVDI(vdiNode)
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
        while (!vdiStreaming && videoDecoder.nextFrameExists) {
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


