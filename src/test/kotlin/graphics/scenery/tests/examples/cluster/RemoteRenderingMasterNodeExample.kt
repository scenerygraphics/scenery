package graphics.scenery.tests.examples.cluster

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.volumes.*
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
class RemoteRenderingMasterNodeExample : SceneryBase("Server", wantREPL = false) {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        //subscriber to the client (instead of using VM parameters, because we need to catch the payload coming from the client (server) and either send it further to
        // the clusters, or deserialize it here
        val serverAddress = "tcp://127.0.0.1"
        val mainPort = 6040
        val backchannelPort = 6041

        val subscriber = NodeSubscriber(hub, serverAddress, mainPort, backchannelPort)
        hub.add(subscriber)

        applicationName = "MasterNode"
        val clusteredRendering = true
        if(clusteredRendering) {
            // publisher to the cluster, needs to send the payload from the client subscriber tp the cluster, if wanted
            val relayMainPort = 6042
            val relayBackchannelPort = 6043
            val relayPublisher = NodePublisher(hub, portMain = relayMainPort, portBackchannel = relayBackchannelPort)
            hub.add(relayPublisher)

            val listening = true
            subscriber.setReceiveTimeout(100)
            var payload: ByteArray?
            thread {
                while (listening) {
                    payload = subscriber.getPayload() ?: continue
                    relayPublisher.send(payload)
                }
            }
            //now we dont want the publisher to go through this scene, deserializing it and sending it to the cluster, but we want to catch the datastream of the subscriber
            //and relay this to the cluster

            }
        else {
            subscriber.startListening()
            scene.postUpdate += { subscriber.networkUpdate(scene) }

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
                while (!sceneInitialized()) {
                    Thread.sleep(200)
                }

                while (true) {
                    val dummyVolume = scene.find("DummyVolume") as? DummyVolume
                    val clientCam = scene.find("ClientCamera") as? DetachedHeadCamera
                    if (dummyVolume != null && clientCam != null) {
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
    }

    override fun main() {
        // add assertions, these only get called when the example is called
        // as part of scenery's integration tests
        assertions[AssertionCheckPoint.AfterClose]?.add {
            val f = File("./RemoteRenderingMasterNodeExample.mp4")
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
            RemoteRenderingMasterNodeExample().main()
        }
    }
}

