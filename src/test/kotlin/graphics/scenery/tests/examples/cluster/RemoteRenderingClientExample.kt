package graphics.scenery.tests.examples.cluster

import graphics.scenery.*
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.primitives.Plane
import graphics.scenery.tests.examples.basic.TexturedCubeExample
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.VideoDecoder
import graphics.scenery.volumes.DummyVolume
import graphics.scenery.volumes.TransferFunction
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3i
import java.io.FileNotFoundException
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.test.assertTrue

/**
 * Texture cube, but with network sync * * Start master with vm param: * -ea -Dscenery.Server=true * * For client see [SlimClient]
 */
class RemoteRenderingClientExample : SceneryBase("RemoteRenderingClientExample", wantREPL = false) {

    var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)
    var decodedFrameCount: Int = 0

    override fun init() {
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512)
        )

        val shell = Box(Vector3f(20.0f, 20.0f, 20.0f), insideNormals = true)
        with(shell) {
            wantsSync = false
            material() {
                cullingMode = Material.CullingMode.None
                diffuse = Vector3f(0.2f, 0.2f, 0.2f)
                specular = Vector3f(0.0f)
                ambient = Vector3f(0.0f)
            }
        }
        scene.addChild(shell)

        val light = PointLight(radius = 15.0f)
        with(light) {
            spatial() {
                position = Vector3f(0.0f, 0.0f, 2.0f)
            }
            intensity = 5.0f
            emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            scene.addChild(this)
        }

        val billBoard = Billboard(Vector2f(2.0f, 2.0f), Vector3f(0.0f, 0.0f, 0.0f), true)
        with(billBoard)
        {
            wantsSync = false
            material {
                textures["diffuse"] = Texture.fromImage(Image.fromResource("textures/helix.png", TexturedCubeExample::class.java))
            }

            scene.addChild(this)
        }

        val dummyVolume = DummyVolume(0)
        with(dummyVolume) {
            name = "DummyVolumeOne"
            transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
            scene.addChild(this)
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 0.0f)
            }
            perspectiveCamera(50.0f, 512, 512)
            wantsSync = true
            scene.addChild(this)
        }

        val videoDecoder = VideoDecoder("udp://${InetAddress.getLocalHost().hostAddress}:3337")
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
                    drawFrame(image, videoDecoder.videoWidth, videoDecoder.videoHeight, billBoard, decodedFrameCount)
                    decodedFrameCount++
                }
            }
            decodedFrameCount -= 1
            logger.info("Done decoding and displaying $decodedFrameCount frames.")
        }

        // transfer function manipulation
        thread {
            while (running) {
                dummyVolume.transferFunction = TransferFunction.ramp(0.001f + (dummyVolume.counter++%1000)/1000.0f, 0.5f, 0.3f)
                Thread.sleep(20)
            }
        }
    }

    private fun drawFrame(tex: ByteArray, width: Int, height: Int, plane: Billboard, frameIndex: Int) {

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

    override fun main() {
        // add assertions, these only get called when the example is called
        // as part of scenery's integration tests
        assertions[AssertionCheckPoint.AfterClose]?.add {
            assertTrue ( decodedFrameCount == 105, "All frames of the video were read and decoded" )
        }

        super.main()
    }

companion object {
    @JvmStatic
    fun main(args: Array<String>) {
        RemoteRenderingClientExample().main()
    }
}
}
