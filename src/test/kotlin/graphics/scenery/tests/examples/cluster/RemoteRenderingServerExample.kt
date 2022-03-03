package graphics.scenery.tests.examples.cluster

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.DummyVolume
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import java.io.File
import java.net.InetAddress
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.test.assertTrue

/**
 * Empty scene to receive content via network
 *
 * Start with vm param:
 * -ea -Dscenery.ServerAddress=tcp://127.0.0.1 [-Dscenery.RemoteCamera=false|true]
 *
 * Explanation:
 * - RemoteCamera: (default false) Has to be set to true if the camera of the server provided scene should be used.
 */
class RemoteRenderingServerExample : SceneryBase("Client", wantREPL = false) {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        if(!Settings().get("RemoteCamera",false)) {
            val cam: Camera = DetachedHeadCamera()
            with(cam) {
                spatial {
                    position = Vector3f(0.0f, 0.0f, 0.0f)
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
        scene.addChild(volume)

        volume.update += {
            val dummyVolume = scene.find("DummyVolumeOne") as? DummyVolume
            if (dummyVolume != null && volume.transferFunction != dummyVolume.transferFunction){
                volume.transferFunction = dummyVolume.transferFunction
            }
        }

        settings.set("VideoEncoder.StreamVideo", true)
        settings.set("VideoEncoder.StreamingAddress", "udp://${InetAddress.getLocalHost().hostAddress}:3337")

        thread {
            while(renderer?.firstImageReady == false) {
                Thread.sleep(50)
            }
            Thread.sleep(2000)
            renderer?.recordMovie()
        }
    }

    override fun main() {
        // add assertions, these only get called when the example is called
        // as part of scenery's integration tests
        assertions[AssertionCheckPoint.AfterClose]?.add {
            val f = File("./RemoteRenderingServerExample.mp4")
            try {
                assertTrue(f.length() > 0, "Size of recorded video is larger than zero.")
            } finally {
                if(f.exists()) {
                    f.delete()
                }
            }
        }

        super.main()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            RemoteRenderingServerExample().main()
        }
    }
}

