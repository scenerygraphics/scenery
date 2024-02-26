package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.ui.SwingBridgeFrame
import graphics.scenery.utils.VideoDecoder
import graphics.scenery.volumes.DummyVolume
import graphics.scenery.volumes.RemoteRenderingProperties
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.TransferFunctionEditor
import graphics.scenery.volumes.vdi.VDIData
import graphics.scenery.volumes.vdi.VDINode
import graphics.scenery.volumes.vdi.VDIStreamer
import org.joml.Vector3f
import org.joml.Vector3i
import org.scijava.ui.behaviour.ClickBehaviour
import org.zeromq.ZContext
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * An example client that can be used for remote volume rendering. Capable of switching between two visualization modes:
 * receiving an encoded video stream and displaying it, and receiving Volumetric Depth Images (VDIs) and rendering them.
 *
 * Can be used with [ServerApplication].
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de> and Wissal Salhi
 */
class ClientApplication : SceneryBase("Client Application", 512, 512)  {

    var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)
    val context = ZContext(4)

    val numSupersegments = 20
    var vdiStreaming = AtomicBoolean(true)
    var firstVDIStream = true

    val displayPlane = FullscreenObject()
    lateinit var vdiNode: VDINode

    val remoteRenderingProperties = RemoteRenderingProperties()

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
        cam.farPlaneDistance = 20.0f

        //Step 2: Create necessary video-streaming components
        val dummyVolume = DummyVolume()
        with(dummyVolume) {
            name = "DummyVolume"
            transferFunction = TransferFunction.ramp(0.1f, 0.5f)
            scene.addChild(this)
        }

        with(displayPlane) {
            name = "VRplane"
            displayPlane.wantsSync = false
            scene.addChild(this)
        }

        val bridge = SwingBridgeFrame("1DTransferFunctionEditor")
        val transferFunctionUI = TransferFunctionEditor(dummyVolume)
        bridge.addPanel(transferFunctionUI)
        transferFunctionUI.name = dummyVolume.name
        val swingUiNode = bridge.uiNode
        swingUiNode.spatial() {
            position = Vector3f(2f, 0f, 0f)
        }

        val vdiData = VDIData()

        //Step 3: Create vdi node and its properties
        vdiNode = VDINode(windowWidth, windowHeight, numSupersegments, vdiData)
        scene.addChild(vdiNode)

        //Attaching empty textures as placeholders for VDIs before actual VDIs arrive so that rendering can begin
        vdiNode.attachEmptyTextures(VDINode.DoubleBuffer.First)
        vdiNode.attachEmptyTextures(VDINode.DoubleBuffer.Second)

        vdiNode.skip_empty = false

        val VDIPlane = FullscreenObject()
        with(VDIPlane) {
            name = "VDIplane"
            wantsSync = false
            material().textures["diffuse"] = vdiNode.material().textures["OutputViewport"]!!
        }

        //Step 4: add RemoteRenderingProperties Node to scene
        scene.addChild(remoteRenderingProperties)

        vdiStreaming.set(false)

        remoteRenderingProperties.streamType = RemoteRenderingProperties.StreamType.VDI
        var currentMode = RemoteRenderingProperties.StreamType.None

        val vdiStreamer = VDIStreamer()

        thread {

            while (!renderer!!.firstImageReady) {
                Thread.sleep(50)
            }

            //Step 5: switching code
            renderer!!.postRenderLambdas.add {
                if (currentMode != RemoteRenderingProperties.StreamType.VolumeRendering
                    && remoteRenderingProperties.streamType == RemoteRenderingProperties.StreamType.VolumeRendering
                ) {

                    logger.info("Switching to Volume Rendering")

                    vdiStreaming.set(false)
                    scene.addChild(displayPlane)
                    scene.removeChild(vdiNode)
                    scene.removeChild(VDIPlane)

                    thread {
                        decodeVideo(displayPlane)
                    }
                    currentMode = RemoteRenderingProperties.StreamType.VolumeRendering
                } else if (currentMode != RemoteRenderingProperties.StreamType.VDI
                    && remoteRenderingProperties.streamType == RemoteRenderingProperties.StreamType.VDI
                ) {

                    logger.info("Switching to VDI streaming")

                    vdiStreaming.set(true)
                    scene.addChild(vdiNode)
                    scene.addChild(VDIPlane)
                    scene.removeChild(displayPlane)

                    if (firstVDIStream) {
                        thread {
                            vdiStreamer.receiveAndUpdateVDI(
                                vdiNode,
                                "tcp://localhost:6655",
                                renderer!!,
                                windowWidth,
                                windowHeight,
                                numSupersegments
                            )
                        }
                        firstVDIStream = false
                    }
                    currentMode = RemoteRenderingProperties.StreamType.VDI
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
        while (!(vdiStreaming.get()) && videoDecoder.nextFrameExists) {
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

        inputHandler?.addBehaviour("change_mode",
            ClickBehaviour { _, _ ->
                if(remoteRenderingProperties.streamType == RemoteRenderingProperties.StreamType.VolumeRendering) {
                    remoteRenderingProperties.streamType = RemoteRenderingProperties.StreamType.VDI
                } else {
                    remoteRenderingProperties.streamType = RemoteRenderingProperties.StreamType.VolumeRendering
                }
            })
        inputHandler?.addKeyBinding("change_mode", "T")
    }
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ClientApplication().main()
        }
    }
}


