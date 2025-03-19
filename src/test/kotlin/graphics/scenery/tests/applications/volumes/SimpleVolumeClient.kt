package graphics.scenery.tests.applications.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.ui.SwingBridgeFrame
import graphics.scenery.utils.VideoDecoder
import graphics.scenery.volumes.DummyVolume
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.TransferFunctionEditor
import org.joml.Vector3f
import org.joml.Vector3i
import java.io.File
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Simple client application for remote server-side volume rendering. Receives and displays a video stream from
 * the server (e.g. [SimpleVolumeServerExample]). Uses a [DummyVolume] to capture changes in volume rendering
 * parameters that are synchronized with the server. Camera is also synchronized with the server.
 *
 * Start with vm param:
 * -Dscenery.Server=true
 *
 * Explanation:
 * This application, the client in the remote volume rendering setup, is the server in scenery's networking code
 * because the camera and volume properties from this scene need to be used in the remote rendering server.
 */
class SimpleVolumeClient : SceneryBase("Volume Client", 512, 512) {

    val displayPlane = FullscreenObject()
    var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)

    override fun init() {

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            name = "ClientCamera"
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)
            wantsSync = true
            scene.addChild(this)
        }

        val dummyVolume = DummyVolume()
        with(dummyVolume) {
            name = "DummyVolume"
            transferFunction = TransferFunction.ramp(0.1f, 0.5f)
            scene.addChild(this)
        }

        val bridge = SwingBridgeFrame("1DTransferFunctionEditor")
        val tfUI = TransferFunctionEditor(dummyVolume)
        bridge.addPanel(tfUI)
        tfUI.name = dummyVolume.name
        val swingUiNode = bridge.uiNode
        swingUiNode.spatial() {
            position = Vector3f(2f, 0f, 0f)
        }

        with(displayPlane){
            name = "plane"
            wantsSync = false
            scene.addChild(this)
        }

        thread {
            while(!renderer!!.firstImageReady) {
                Thread.sleep(50)
            }
            logger.info("Searching for SDP file: scenery-stream.sdp. Please make sure the appropriate SDP file for the " +
                "streaming video is present in the working directory.")

            while (!File("scenery-stream.sdp").exists()) {
                Thread.sleep(100)
            }

            logger.info("Found SDP file. Starting video decoding.")
            val videoDecoder = VideoDecoder("scenery-stream.sdp")

            videoDecoder.decodeFrameByFrame(drawFrame)
        }
    }

     private val drawFrame: (ByteArray, Int, Int, Int) -> Unit = {tex: ByteArray, width: Int, height: Int, frameIndex: Int ->
         if(frameIndex % 100 == 0) {
             logger.debug("Displaying frame $frameIndex")
         }

        if(buffer.capacity() == 0) {
            buffer = BufferUtils.allocateByteAndPut(tex)
        } else {
            buffer.put(tex).flip()
        }

        displayPlane.material {
            textures["diffuse"] = Texture(Vector3i(width, height, 1), 4, contents = buffer, mipmap = true)
        }
    }

    /**
     * Companion object for providing a main method.
     */
    companion object {
        /**
         * The main entry point. Executes this example application when it is called.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            SimpleVolumeClient().main()
        }
    }

}
