package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.ui.SwingBridgeFrame
import graphics.scenery.utils.VideoDecoder
import graphics.scenery.volumes.*
import org.joml.Vector3f
import org.joml.Vector3i
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 *  Empty scene to receive content via network and update the content via transferFunction editor and dummy volume
 *
 * Start with vm param:
 * -ea -Dscenery.ServerAddress=tcp://127.0.0.1 [-Dscenery.RemoteCamera=false|true]
 *
 * Explanation:
 * - RemoteCamera: (default false) Has to be set to true if the camera of the server provided scene should be used.
 */
class SimpleVolumeClient : SceneryBase("Volume Client", 512 , 512) {

    var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)
    var decodedFrameCount: Int = 0

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
            position = Vector3f(2f,0f,0f)
        }

        val plane = FullscreenObject()
        with(plane){
            name = "plane"
            wantsSync = false
            scene.addChild(this)
        }

        decodeVideo(plane)

    }

    private fun decodeVideo(plane: FullscreenObject){
        val videoDecoder = VideoDecoder("scenery-stream.sdp")
        logger.info("video decoder object created")
        thread {
            while (!sceneInitialized()) {
                Thread.sleep(200)
            }

            decodedFrameCount = 1

            while (videoDecoder.nextFrameExists) {
                val image = videoDecoder.decodeFrame()  /* the decoded image is returned as a ByteArray, and can now be processed.
                                                        Here, it is simply displayed in fullscreen */
                if(image != null) { // image can be null, e.g. when the decoder encounters invalid information between frames
                    drawFrame(image, videoDecoder.videoWidth, videoDecoder.videoHeight, plane, decodedFrameCount)
                    decodedFrameCount++
                }
            }
            decodedFrameCount -= 1
            logger.info("Done decoding and displaying $decodedFrameCount frames.")
        }
    }

    private fun drawFrame(tex: ByteArray, width: Int, height: Int, plane: FullscreenObject, frameIndex: Int) {

        if(frameIndex % 100 == 0) {
            logger.info("Displaying frame $frameIndex")
        }

        if(buffer.capacity() == 0) {
            buffer = BufferUtils.allocateByteAndPut(tex)
        } else {
            buffer.put(tex).flip()
        }

        plane.material {
            textures["diffuse"] = Texture(Vector3i(width, height, 1), 4, contents = buffer, mipmap = true)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SimpleVolumeClient().main()
        }
    }

}