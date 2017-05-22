package graphics.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.volumes.DirectVolume
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
    var hmd: OpenVRHMD? = null

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, 1920, 1200)
        hub.add(SceneryElement.Renderer, renderer!!)

//        hmd = OpenVRHMD(useCompositor = true)
//        hub.add(SceneryElement.HMDInput, hmd!!)

        val shell = Box(GLVector(120.0f, 120.0f, 120.0f), insideNormals = true)
        shell.material.doubleSided = true
        shell.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        shell.material.specular = GLVector.getNullVector(3)
        shell.material.ambient = GLVector.getNullVector(3)
        scene.addChild(shell)

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512.0f, 512.0f)
            active = true

            scene.addChild(this)
        }

        val volume = DirectVolume()

        with(volume) {
            volume.readFrom(Paths.get("/Users/ulrik/Desktop/stack_00100.raw"))
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

    }

    @Test override fun main() {
        super.main()
    }
}
