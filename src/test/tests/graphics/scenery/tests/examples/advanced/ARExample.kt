package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.Hololens
import graphics.scenery.volumes.Volume
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.concurrent.thread

/**
 * Standard volume rendering example, rendered on [Hololens].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ARExample: SceneryBase("AR Volume Rendering example", 2560, 720) {
    var hmd: Hololens = Hololens()

    override fun init() {
        hub.add(SceneryElement.HMDInput, hmd)
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = GLVector(0.0f, 0.5f, 2.5f)
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight)
            active = true

            scene.addChild(this)
        }

        val volume = Volume()
        volume.name = "volume"
        volume.colormap = "jet"
        volume.position = GLVector(0.0f, 0.8f, -1.0f)
        volume.scale = GLVector(0.1f, 0.1f, 0.1f)
        scene.addChild(volume)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 150.0f
            scene.addChild(light)
        }

        val files: List<File> = File(getDemoFilesPath() + "/volumes/box-iso/").listFiles().toList()

        val volumes = files.filter { it.isFile }.map { it.absolutePath }.sorted()
        logger.info("Got ${volumes.size} volumes: ${volumes.joinToString(", ")}")

        var currentVolume = 0
        fun nextVolume(): String {
            val v = volumes[currentVolume % (volumes.size)]
            currentVolume++

            return v
        }

        thread {
            while(!scene.initialized || volumes.isEmpty()) { Thread.sleep(200) }

            val v = nextVolume()
            volume.readFrom(Paths.get(v), replace = true)

            logger.info("Got volume!")

            while(true) {
                volume.rotation = volume.rotation.rotateByAngleY(0.01f)
                Thread.sleep(5)
            }
        }

    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    @Test override fun main() {
        super.main()
    }
}
