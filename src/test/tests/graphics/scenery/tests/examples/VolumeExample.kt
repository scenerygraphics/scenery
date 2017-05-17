package graphics.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.Volume
import org.junit.Test
import java.nio.file.Paths
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VolumeExample: SceneryDefaultApplication("Volume Rendering example") {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, 512, 512)
        hub.add(SceneryElement.Renderer, renderer!!)

        val shell = Box(GLVector(20.0f, 20.0f, 20.0f), insideNormals = true)
        shell.material.doubleSided = true
        shell.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        shell.material.specular = GLVector.getNullVector(3)
        shell.material.ambient = GLVector.getNullVector(3)
        scene.addChild(shell)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512.0f, 512.0f)
            active = true

            scene.addChild(this)
        }

        val volume = Volume()

        with(volume) {
            volume.readFrom(Paths.get("/Users/ulrik/Desktop/t1-head.raw"))
            scene.addChild(this)
        }

        val lights = (0..3).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(4.0f * i, 4.0f * i, 4.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 500.2f*(i+1)
            light.linear = 1.8f
            light.quadratic = 0.7f
            scene.addChild(light)
        }

        volume.update = { volume.render() }

        thread {
            while (true) {
                Thread.sleep(20)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
