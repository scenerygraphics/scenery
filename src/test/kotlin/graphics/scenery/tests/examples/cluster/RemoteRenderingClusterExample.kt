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
 * This example resembles the server site interface a client connects to, to render large volume data on a remote server
 * The client than gets back a video stream (for now), which it can use to get a window into the scene of the rendering server
 *
 * This example also shows a way (work in progress), to distibute the data coming from the client (transfer function, lights, ...)
 * to cluster nodes connected to the master node (this is an example of a master node)
 * The master node should still be able to render on its own, without dependency on the cluster
 */
class RemoteRenderingClusterExample : SceneryBase("Cluster", wantREPL = false) {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }

        val volume = Volume.fromPathRaw(Paths.get(getDemoFilesPath() + "/volumes/box-iso/"), hub)
        with(volume) {
            name = "volume"
            colormap = Colormap.get("viridis")
            transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
            spatial() {
                position = Vector3f(0.0f, 0.0f, 0.0f)
                rotation = rotation.rotateXYZ(0.05f, 0.05f, 0.05f)
                scale = Vector3f(20.0f, 20.0f, 20.0f)
            }
            scene.addChild(this)
        }

        thread {
            while(!sceneInitialized()) {
                Thread.sleep(200)
            }

            while(true) {
                val dummyVolume = scene.find("DummyVolume") as? DummyVolume
                val clientCam = scene.find("ClientCamera") as? DetachedHeadCamera
                if(dummyVolume != null && clientCam != null) {
                    volume.update += {
                        if (volume.transferFunction != dummyVolume.transferFunction) {
                            volume.transferFunction = dummyVolume.transferFunction
                        }
                        /*
                        if(volume.colormap != dummyVolume.colormap) {
                            volume.colormap = dummyVolume.colormap
                        }
                        if(volume.slicingMode != dummyVolume.slicingMode) {
                            volume.slicingMode = dummyVolume.slicingMode
                        }*/
                    }
                    cam.update += {
                        cam.spatial().position = clientCam.spatial().position
                        cam.spatial().rotation = clientCam.spatial().rotation
                    }
                    break;
                }
            }
            settings.set("VideoEncoder.StreamVideo", true)
            settings.set("VideoEncoder.StreamingAddress", "udp://${InetAddress.getLocalHost().hostAddress}:3337")
            renderer?.recordMovie()
        }
    }

    override fun main() {
        // add assertions, these only get called when the example is called
        // as part of scenery's integration tests
        assertions[AssertionCheckPoint.AfterClose]?.add {
            //assertTrue ( decodedFrameCount == 105, "All frames of the video were read and decoded" )
        }
        super.main()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            RemoteRenderingClusterExample().main()
        }
    }
}

