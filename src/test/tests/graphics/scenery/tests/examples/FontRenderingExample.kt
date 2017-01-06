package graphics.scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.*
import org.junit.Test
import graphics.scenery.backends.Renderer
import graphics.scenery.repl.REPL

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class FontRenderingExample: SceneryDefaultApplication("FontRenderingExample") {
    override fun init() {
        renderer = Renderer.createRenderer(applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.RENDERER, renderer!!)

        var lights = (0..5).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
            light.intensity = 0.2f * (i + 1);
            scene.addChild(light)
        }

        val hullbox = Box(GLVector(900.0f, 900.0f, 900.0f))
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
            position = GLVector(0.0f, 0.0f, -25.0f)
            view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)
            projection = GLMatrix()
                    .setPerspectiveProjectionMatrix(
                            70.0f / 180.0f * Math.PI.toFloat(),
                            1024f / 1024f, 0.1f, 1000.0f)
            active = true

            scene.addChild(this)
        }

        val board = FontBoard()
        board.text = "hello, world!"

        scene.addChild(board)

        repl = REPL(scene, renderer!!)
        repl?.start()
        repl?.showConsoleWindow()
        repl?.eval("var fontBoard = scene.find(\"FontBoard\");")
        repl?.eval("print(\"Font Example - try e.g. fontBoard.fontColor = new GLVector(1.0, 0.0, 0.0);\");")
    }

    @Test override fun main() {
        super.main()
    }
}
