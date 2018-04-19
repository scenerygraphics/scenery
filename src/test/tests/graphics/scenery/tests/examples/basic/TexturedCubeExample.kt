package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import org.junit.Test
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TexturedCubeExample : SceneryBase("TexturedCubeExample") {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, 512, 512)
        hub.add(SceneryElement.Renderer, renderer!!)

        val boxmaterial = Material().apply {
            ambient = GLVector(1f, 0f, 0f)
            diffuse = GLVector(0f, 1f, 0f)
            specular = GLVector(1f, 1f, 1f)
            roughness = 0.3f
            metallic = 1f
            textures["diffuse"] = TexturedCubeExample::class.java.getResource("textures/helix.png").file
        }

        val box = Box(GLVector(1f, 1f, 1f)).apply {
            name = "le box du win"
            material = boxmaterial
            scene.addChild(this)
        }

        val light = PointLight(radius = 15f).apply {
            position = GLVector(0f, 0f, 2f)
            intensity = 100f
            emissionColor = GLVector(1f, 1f, 1f)
        }
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera().apply {
            position = GLVector(0f, 0f, 5f)
            perspectiveCamera(50f, 512f, 512f)
            active = true

            scene.addChild(this)
        }

        thread {
            while (true) {
                box.rotation.rotateByAngleY(0.01f)
                box.needsUpdate = true

                Thread.sleep(20)
            }
        }
    }

    @Test
    override fun main() {
        super.main()
    }
}
