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
class FontRenderingExample: SceneryBase("FontRenderingExample", windowWidth = 1280, windowHeight = 720) {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        var lights = (0..5).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(5.0f + i*2.0f, 5.0f, 5.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 1000.0f
            light.quadratic = 0.001f
            light.linear = 0.0f
            scene.addChild(light)
        }

        val hullbox = Box(GLVector(40.0f, 40.0f, 40.0f), insideNormals = true)
        hullbox.position = GLVector(0.1f, 0.1f, 0.1f)
        val hullboxM = Material()
        hullboxM.ambient = GLVector(1.0f, 1.0f, 1.0f)
        hullboxM.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        hullboxM.specular = GLVector(1.0f, 1.0f, 1.0f)
        hullboxM.doubleSided = true
        hullbox.material = hullboxM

        scene.addChild(hullbox)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(3.3f, 0.0f, 4.0f)
            perspectiveCamera(70.0f, windowWidth*1.0f, windowHeight*1.0f, 1.0f, 1000.0f)
            active = true

            scene.addChild(this)
        }

        val board = FontBoard()
        board.text = "hello world"
        board.name = "FontBoard"
        board.transparent = 0
        board.fontColor = GLVector(100.0f, 100.0f, 100.0f, 1.0f)
        board.backgroundColor = GLVector(0.0f, 0.0f, 0.0f, 1.0f)
        board.position = GLVector(0.0f, 0.0f, 0.0f)

        scene.addChild(board)

        thread {
            while(board.dirty) { Thread.sleep(200) }

            val text = arrayOf(
                "this is scenery.",
                "with sdf font rendering.",
                "hello world."
            )

            while(running) {
                text.map {
                    Thread.sleep(2500)
                    board.text = it
                }
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
