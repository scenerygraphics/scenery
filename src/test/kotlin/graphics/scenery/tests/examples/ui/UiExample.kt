package graphics.scenery.tests.examples.ui

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.tests.examples.basic.TexturedCubeExample
import graphics.scenery.textures.Texture
import graphics.scenery.ui.MenuMesh
import graphics.scenery.utils.Image
import org.joml.Vector3f
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class UiExample : SceneryBase("TexturedCubeExample") {

    override fun init() {

        renderer = hub.add(SceneryElement.Renderer, Renderer.createRenderer(hub, applicationName, scene, 2560, 1440))


        val box = Box(Vector3f(1.0f, 1.0f, 1.0f)).apply {
            name = "le box du win"
            material {
                textures["diffuse"] = Texture.fromImage(Image.fromResource("textures/helix.png", TexturedCubeExample::class.java))
                metallic = 0.3f
                roughness = 0.9f
            }
        }
        scene.addChild(box)

        val light = PointLight(radius = 15.0f).apply {
            spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
            intensity = 5.0f
            emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        }
        scene.addChild(light)

        val cam = DetachedHeadCamera().apply {
            spatial().position = Vector3f(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512, 512)
        }
        scene.addChild(cam)

        /* I'd like to be able to do:
        val menu = MenuMesh(hub) {
            with(ImGui) {
               // do imgui stuff
            }
        }
        */

        val menu = MenuMesh(hub)
        scene.addChild(menu)

        thread {
            while (running) {
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
            UiExample().main()
        }
    }
}

