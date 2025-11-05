package graphics.scenery.tests.examples.network

import graphics.scenery.*
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import org.joml.Vector3f
import kotlin.concurrent.thread

/**
 * Texture cube, but with network sync
 *
 * Start master with vm param:
 * -Dscenery.Server=true
 *
 * For client see [SlimClient]
 */
class SimpleNetworkExample : SceneryBase("SimpleNetworkExample", wantREPL = false) {
    override fun init() {
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512)
        )

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        with(box) {
            name = "le box du win"
            material {
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
            spatial {
                rotation.rotateY(0.5f)
                needsUpdate = true
            }
            scene.addChild(this)
        }

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
            wantsSync = true
            scene.addChild(this)
        }

        // box rotation
        thread {
            while (running) {
                box.spatial {
                    rotation.rotateY(0.01f)
                    needsUpdate = true
                }

                Thread.sleep(20)
            }
        }

        thread {
            Thread.sleep(6000)
            val ma = DefaultMaterial()
            ma.diffuse = Vector3f(0f, 1f, 0f)
            //box.setMaterial(ma)
            box.spatial().needsUpdate = true
            println("replacing Mat")
        }

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SimpleNetworkExample().main()
        }
    }
}

