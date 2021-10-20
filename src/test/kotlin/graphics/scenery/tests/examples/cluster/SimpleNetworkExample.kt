package graphics.scenery.tests.examples.cluster

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import kotlin.concurrent.thread

/**
 * Texture cube, but with network sync
 *
 * Start master with vm param:
 * -ea -Dscenery.master=true
 *
 * Start slave with vm param:
 * -ea -Dscenery.master=false -Dscenery.MasterNode=tcp://127.0.0.1:6666
 */
class SimpleNetworkExample : SceneryBase("TexturedCubeExample", wantREPL = false) {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        box.material {
            textures["diffuse"] = Texture.fromImage(Image.fromResource("../basic/textures/helix.png", SimpleNetworkExample::class.java))
            metallic = 0.3f
            roughness = 0.9f
        }
        scene.addChild(box)

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
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

        val publisher = hub.get<NodePublisher>(SceneryElement.NodePublisher)
        val subscriber = hub.get<NodeSubscriber>(SceneryElement.NodeSubscriber)

        if (settings.get<Boolean>("master")){
            publisher?.nodes?.put(13337, box)
        }else{
            subscriber?.nodes?.put(13337, box)
        }

        thread {
            while (running && settings.get<Boolean>("master")) {
                box.spatial {
                    rotation.rotateY(0.01f)
                    needsUpdate = true
                }
                box.modifiedAt = System.nanoTime()

                Thread.sleep(20)
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SimpleNetworkExample().main()
        }
    }
}

