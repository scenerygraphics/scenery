package graphics.scenery.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.scenery.*
import org.junit.Test
import graphics.scenery.scenery.backends.Renderer
import graphics.scenery.scenery.repl.REPL
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TexturedArrowExample : SceneryDefaultApplication("TexturedArrowExample") {
    override fun init() {
        renderer = Renderer.createRenderer(applicationName, scene, 512, 512)
        hub.add(SceneryElement.RENDERER, renderer!!)

        var mat= Material()
        with(mat) {
            ambient = GLVector(1.0f, 0.0f, 0.0f)
            diffuse = GLVector(0.0f, 1.0f, 0.0f)
            specular = GLVector(1.0f, 1.0f, 1.0f)
            textures.put("diffuse", TexturedCubeExample::class.java.getResource("textures/helix.png").file)
        }

        var cylinder = Cylinder(radius = 1.0f, height = 2.0f, segments = 50)
        var tip = Cone(radius = 1.2f, height = 1.5f, segments = 50)

        with(cylinder) {
            position = GLVector(0.0f, -2.0f, 0.0f)
            material = mat
            scene.addChild(this)
        }

        with(tip) {
            position = GLVector(0.0f, 0.0f, 0.0f)
            material = mat
            scene.addChild(this)
        }

        var lights = (0..2).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
            light.intensity = 0.2f*(i+1);
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512.0f, 512.0f)
            active = true

            scene.addChild(this)
        }

        thread {
            while (true) {
                cylinder.rotation.rotateByAngleY(0.01f)
                tip.rotation.rotateByAngleY(0.01f)
                cylinder.needsUpdate = true
                tip.needsUpdate = true

                Thread.sleep(20)
            }
        }

        repl = REPL(scene, renderer!!)
        repl?.start()
        repl?.showConsoleWindow()
    }

    @Test override fun main() {
        super.main()
    }
}
