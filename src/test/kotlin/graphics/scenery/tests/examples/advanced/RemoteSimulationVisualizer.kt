package graphics.scenery.tests.examples.advanced

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.tests.examples.basic.TexturedCubeExample
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imagej.Dataset
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.test.assertTrue


class RemoteSimulationVisualizerExample: SceneryBase("RemoteSimulationVisualizerExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 1024, 1024))


        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        box.material {
            metallic = 0.3f
            roughness = 0.9f
        }
        scene.addChild(box)

        val light = PointLight(radius = 15.0f)
        light.spatial {
            position = Vector3f(0.0f, 0.0f, 2.0f)
        }
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }
        settings.set("VideoEncoder.StreamVideo", true)
        settings.set("VideoEncoder.StreamingAddress", "udp://${InetAddress.getLocalHost().hostAddress}:3337")

        val subscriber = hub.get<NodeSubscriber>(SceneryElement.NodeSubscriber)
        subscriber?.nodes?.put(13337, box)
        //subscriber?.nodes?.put(13337, volume)


        /*thread {
            while (true) {
                box.spatial {
                    rotation.rotateY(graphics.scenery.numerics.Random.randomFromRange(-0.04f, 0.04f))
                    rotation.rotateZ(graphics.scenery.numerics.Random.randomFromRange(-0.04f, 0.04f))
                    needsUpdate = true
                }

                volume.spatial {
                    rotation = rotation.rotateY(0.003f)
                }

                Thread.sleep(20)
            }
        }*/

        thread {
            while(renderer?.firstImageReady == false) {
                Thread.sleep(50)
            }
            //renderer?.recordMovie("./RemoteSimulationVisualizerExample.mp4")
            //Thread.sleep(5000)
            //renderer?.recordMovie()
        }
    }

    override fun main() {
        // add assertions, these only get called when the example is called
        // as part of scenery's integration tests
        assertions[AssertionCheckPoint.AfterClose]?.add {
            val f = File("./RemoteSimulationVisualizerExample.mp4")
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
            RemoteSimulationVisualizerExample().main()
        } }
}


