package graphics.scenery.tests.examples.advanced

import graphics.scenery.BufferUtils
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.FullscreenObject
import graphics.scenery.SceneryBase
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.VideoDecoder
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Example to show programmatic video decoding.
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */
class VideoDecodingExample : SceneryBase("VideoDecodingExample", 600, 600, wantREPL = false) {

    var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)

    override fun init () {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val cam = DetachedHeadCamera()

        with(cam) {
            position = Vector3f(-4.365f, 0.38f, 0.62f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        cam.position = Vector3f(3.213f, 8.264E-1f, -9.844E-1f)
        cam.rotation = Quaternionf(3.049E-2, 9.596E-1, -1.144E-1, -2.553E-1)

        val plane = FullscreenObject()
        scene.addChild(plane)

        settings.set("Renderer.HDR.Exposure", 0.05f)

        val videoDecoder = VideoDecoder("/home/aryaman/Desktop/ViC_movie_600.mp4")
        logger.info("video decoder object created")
        thread {
            while (!sceneInitialized()) {
                Thread.sleep(200)
            }

            var cnt = 0

            while (videoDecoder.nextFrameExists) {
                val image = videoDecoder.decodeFrame()
                // the decoded image is returned as a ByteArray, and can now be processed. Here, it is simply displayed in fullscreen

                if(image != null) {
                    drawFrame(image, videoDecoder.videoWidth, videoDecoder.videoHeight, plane, cnt)
                }
                cnt++
            }
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

        plane.material.textures["diffuse"] = Texture(Vector3i(width, height, 1), 4, contents = buffer, mipmap = true)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VideoDecodingExample().main()
        }
    }

}