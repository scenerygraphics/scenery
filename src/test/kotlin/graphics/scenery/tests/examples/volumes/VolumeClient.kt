package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.ui.SwingBridgeFrame
import graphics.scenery.volumes.*
import org.joml.Vector3f
import java.nio.ByteBuffer
import java.nio.file.Paths
import kotlin.concurrent.thread


class VolumeClient : SceneryBase("Volume Client", 512 , 512) {

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

        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
        volume.name = "volume"
        volume.colormap = Colormap.get("viridis")
        volume.spatial {
            position = Vector3f(0.0f, 0.0f, -3.5f)
            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
            scale = Vector3f(20.0f, 20.0f, 20.0f)
        }
        volume.wantsSync = false
        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
        scene.addChild(volume)

        val dummyVolume = DummyVolume()
        with(dummyVolume) {
            name = "DummyVolume"
            transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
            scene.addChild(this)
        }

        //Until we add the missing parameters for dummy volume to become a volume
         val bridge = SwingBridgeFrame("TransferFunctionEditor")
       val tfUI = TransferFunctionEditor(dummyVolume, bridge)
          tfUI.name = dummyVolume.name
        val swingUiNode = tfUI.mainFrame.uiNode
        swingUiNode.spatial() {
            position = Vector3f(2f,0f,0f)
        }

//        //        val plane = FullscreenObject()
////        with(plane){
////            name = "plane"
////            wantsSync = false
////            //scene.addChild(this)
////        }

        thread {
            while (true) {
                volume.transferFunction = dummyVolume.transferFunction
            }
        }

        //val videoDecoder = VideoDecoder("scenery-stream.sdp")
        //val videoDecoder = VideoDecoder(this::class.java.getResource("SampleVideo.mp4")?.sanitizedPath() ?: throw FileNotFoundException("Could not find sample file."))
        //logger.info("video decoder object created")
        //var i : Int = 0;
        //thread {
        //    while (!sceneInitialized()) {
        //        Thread.sleep(200)
        //    }
        //
        //    decodedFrameCount = 1
        //
        //    while (videoDecoder.nextFrameExists) {
        //        val image = videoDecoder.decodeFrame()  /* the decoded image is returned as a ByteArray, and can now be processed.
        //                                                Here, it is simply displayed in fullscreen */
        //
        //        i += 1
        //        if(image != null) { // image can be null, e.g. when the decoder encounters invalid information between frames
        //            drawFrame(image, videoDecoder.videoWidth, videoDecoder.videoHeight, plane, decodedFrameCount)
        //            decodedFrameCount++
        //            logger.warn("scene "+i);
        //        }
        //    }
        //    decodedFrameCount -= 1
        //    logger.info("Done decoding and displaying $decodedFrameCount frames.")
        //}
    }

//    private fun drawFrame(tex: ByteArray, width: Int, height: Int, plane: FullscreenObject, frameIndex: Int) {
//
//        if(frameIndex % 100 == 0) {
//            logger.info("Displaying frame $frameIndex")
//        }
//
//        if(buffer.capacity() == 0) {
//            buffer = BufferUtils.allocateByteAndPut(tex)
//        } else {
//            buffer.put(tex).flip()
//        }
//
//        plane.material {
//            textures["diffuse"] = Texture(Vector3i(width, height, 1), 4, contents = buffer, mipmap = true)
//        }
//    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VolumeClient().main()
        }
    }

}
