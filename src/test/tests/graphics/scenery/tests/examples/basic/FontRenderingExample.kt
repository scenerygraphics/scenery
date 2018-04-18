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

        val light = PointLight(radius = 20.0f)
        light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
        light.intensity = 1000.0f
        light.quadratic = 0.001f
        light.linear = 0.0f
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(3.3f, 0.0f, 4.0f)
            perspectiveCamera(70.0f, windowWidth*1.0f, windowHeight*1.0f, 1.0f, 1000.0f)
            active = true

            scene.addChild(this)
        }

        val box = Box(GLVector(10.0f, 10.0f, 10.0f), insideNormals = true)
        box.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        box.material.doubleSided = true
        box.material.cullingMode = Material.CullingMode.Front
        scene.addChild(box)

        val board = TextBoard()
        board.text = "hello world"
        board.name = "TextBoard"
        board.transparent = 0
        board.fontColor = GLVector(0.0f, 0.0f, 0.0f)
        board.backgroundColor = GLVector(100.0f, 100.0f, 0.0f)
        board.position = GLVector(-4.0f, 0.0f, -4.9f)
        board.scale = GLVector(2.0f, 2.0f, 2.0f)

        scene.addChild(board)

        thread {
            while(board.dirty) { Thread.sleep(200) }

            val text = arrayOf(
                "this is scenery.",
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
