package graphics.scenery.tests.examples.advanced

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.primitives.SSBOTest
import graphics.scenery.tests.examples.basic.TexturedCubeExample
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Konrad Michel <konrad.michel@mailbox.tu-dresden.de>
 */
class SSBOExample : SceneryBase("SSBOExample", wantREPL = false) {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val hull = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        hull.material {
            diffuse = Vector3f(0.2f, 0.2f, 0.2f)
            cullingMode = Material.CullingMode.Front
        }
        scene.addChild(hull)

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        box.material {
            diffuse = Vector3f(1.0f, 0.5f, 0.9f)
            metallic = 0.3f
            roughness = 0.9f
        }
        scene.addChild(box)

        val light = PointLight(radius = 20.0f)
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

        val ssboTestObj = SSBOTest()
        scene.addChild(ssboTestObj)

        thread {
            while (running) {
                //do buffer updating here
                Thread.sleep(20)
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SSBOExample().main()
        }
    }
}


