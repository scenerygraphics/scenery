package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.SceneryJPanel
import org.junit.Test
import java.awt.BorderLayout
import javax.swing.JFrame
import kotlin.concurrent.thread

/**
 * TexturedCubeExample, embedded in a Swing window
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class SwingTexturedCubeExample : SceneryBase("SwingTexturedCubeExample", windowWidth = 512, windowHeight = 512) {
    lateinit var mainFrame: JFrame

    override fun init() {
        mainFrame = JFrame(applicationName)
        mainFrame.setSize(windowWidth, windowHeight)
        mainFrame.layout = BorderLayout()

        val sceneryPanel = SceneryJPanel()
        mainFrame.add(sceneryPanel, BorderLayout.CENTER)
        mainFrame.isVisible = true

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight, embedIn = sceneryPanel))
        renderer?.pushMode = true

        val boxmaterial = Material()
        with(boxmaterial) {
            ambient = GLVector(1.0f, 0.0f, 0.0f)
            diffuse = GLVector(0.0f, 1.0f, 0.0f)
            specular = GLVector(1.0f, 1.0f, 1.0f)
            textures["diffuse"] = Texture.fromImage(Image.fromResource("textures/helix.png", this::class.java))
        }

        val box = Box(GLVector(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"

        with(box) {
            box.material = boxmaterial
            scene.addChild(this)
        }

        val light = PointLight(radius = 15.0f)
        light.position = GLVector(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512.0f, 512.0f)
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

        thread {
            while(renderer?.shouldClose == false) {
                Thread.sleep(200)
            }
        }
    }

    override fun close() {
        mainFrame.dispose()
        super.close()
    }

    @Test override fun main() {
        super.main()
    }
}
