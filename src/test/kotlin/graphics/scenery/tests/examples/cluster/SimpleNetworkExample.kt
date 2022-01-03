package graphics.scenery.tests.examples.cluster

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.Material
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
class SimpleNetworkExample : SceneryBase("SimpleNetworkExample", wantREPL = false) {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        if (settings.get<Boolean>("master")) {
            box.name = "le box du win"
            box.material {
                textures["diffuse"] = Texture.fromImage(
                    Image.fromResource(
                        "../basic/textures/helix.png",
                        SimpleNetworkExample::class.java
                    )
                )
                metallic = 0.3f
                roughness = 0.9f
                //(this as DefaultMaterial).synchronizeTextures = false
            }

            box.spatial {
                rotation.rotateY(0.5f)
                needsUpdate = true
            }
            scene.addChild(box)

            thread {
                Thread.sleep(6000)
                val ma = DefaultMaterial()
                ma.diffuse = Vector3f(0f,1f,0f)
                //box.setMaterial(ma)
                box.spatial().needsUpdate = true
                println("replacing Mat")
            }
            val light = PointLight(radius = 15.0f)
            light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
            light.intensity = 5.0f
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            scene.addChild(light)
        }



        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }


        //val publisher = hub.get<NodePublisher>(SceneryElement.NodePublisher)
        //val subscriber = hub.get<NodeSubscriber>(SceneryElement.NodeSubscriber)

        try {
            if (settings.get<Boolean>("master")) {
                val publisher = NodePublisher(hub)
                hub.add(publisher)
                publisher?.startPublishing()
                //Thread.sleep(1000)
                publisher?.register(scene)
                scene.postUpdate += { publisher.scanForChanges()}
                /*thread {
                    while (true) {
                        publisher.scanForChanges()
                        Thread.sleep(500)
                    }
                }*/
            } else {
                val subscriber = NodeSubscriber(hub)
                hub.add(subscriber)
                subscriber.startListening()
                scene.postUpdate += {subscriber.networkUpdate(scene)}
//                thread {
//                    while (true) {
//                        subscriber?.networkUpdate(scene)
//                        Thread.sleep(500)
//                    }
//                }
            }
        } catch (t: Throwable){
            t.printStackTrace()
        }

        thread {
            while (running && settings.get<Boolean>("master")) {
                box.spatial {
                    rotation.rotateY(0.01f)
                    needsUpdate = true
                }

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

