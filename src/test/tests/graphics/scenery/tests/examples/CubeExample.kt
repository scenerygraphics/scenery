package graphics.scenery.tests.examples

import cleargl.GLMatrix
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
class CubeExample : SceneryDefaultApplication("CubeExample") {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        var box = Box(GLVector(1.0f, 1.0f, 1.0f))

        var boxmaterial = Material()
        boxmaterial.ambient = GLVector(1.0f, 0.0f, 0.0f)
        boxmaterial.diffuse = GLVector(0.0f, 1.0f, 0.0f)
        boxmaterial.specular = GLVector(1.0f, 1.0f, 1.0f)
        box.position = GLVector(0.0f, 0.0f, 0.0f)
        box.material = boxmaterial

        scene.addChild(box)

        var lights = (0..0).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(0.0f, 0.1f, 0.8f)
            light.intensity = 0.8f;
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        cam.position = GLVector(0.0f, 0.0f, -5.0f)
        cam.perspectiveCamera(70.0f, 1.0f*windowWidth, 1.0f*windowHeight, 1.0f, 100.0f)
        cam.active = true

        scene.addChild(cam)

        thread {
            while (true) {
                box.rotation.rotateByAngleY(0.01f)
                box.needsUpdate = true

                Thread.sleep(20)
            }
        }
    }


    @Test override fun main() {
        super.main()
    }
}
