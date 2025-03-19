package graphics.scenery.tests.applications.volumes

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.DummyVolume
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import org.joml.Vector3f
import java.nio.file.Paths
import kotlin.concurrent.thread

/**
 * Simple server application for remote server-side volume rendering. Renders a volume and transmits the rendered
 * image as an encoded video stream. Obtains volume rendering parameters and camera by synchronizing with the
 * remote client.
 *
 * Example client: [SimpleVolumeClient]
 *
 * Start master with vm param:
 * -Dscenery.ServerAddress={client's IP address} -Dscenery.RemoteCamera=true
 *
 * Explanation:
 * This application, the server in the remote volume rendering setup, is the client in scenery's networking code
 * because it uses camera and volume rendering parameters from the remote client.
 */
class SimpleVolumeServerExample : SceneryBase("Volume Server Example", 512, 512) {

    override fun init() {
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        )

        if(!Settings().get("RemoteCamera", false)) {
            val cam: Camera = DetachedHeadCamera()
            with(cam) {
                spatial {
                    position = Vector3f(0.0f, 0.0f, 5.0f)
                }
                perspectiveCamera(50.0f, 512, 512)
                scene.addChild(this)
            }
        }

        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
        volume.name = "volume"
        volume.colormap = Colormap.get("viridis")
        volume.spatial {
            position = Vector3f(0.0f, 0.0f, -3.5f)
            rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
            scale = Vector3f(20.0f, 20.0f, 20.0f)
        }
        volume.transferFunction = TransferFunction.ramp(0.1f, 0.5f)
        scene.addChild(volume)

        settings.set("VideoEncoder.StreamVideo", true)
        settings.set("VideoEncoder.StreamingAddress", "rtp://" + Settings().get("ServerAddress", "127.0.0.1").toString().replaceFirst(Regex("^[a-zA-Z]+://"), "") + ":5004")
        renderer!!.recordMovie()

        renderer!!.runAfterRendering.add {
            val dummyVolume = scene.find("DummyVolume") as? DummyVolume
            if (dummyVolume != null) {
                volume.transferFunction = dummyVolume.transferFunction
                volume.maxDisplayRange = dummyVolume.maxDisplayRange
                volume.minDisplayRange = dummyVolume.minDisplayRange
                volume.colormap = dummyVolume.colormap
            }
        }

        thread {
            while(true) {
                volume.spatial {
                    rotation = rotation.rotateY(0.003f)
                }
                Thread.sleep(5)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
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
            SimpleVolumeServerExample().main()
        }
    }
}
