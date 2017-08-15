package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.volumes.Volume
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VolumeExample: SceneryBase("Volume Rendering example") {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, 1280, 720)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = GLVector(0.0f, -1.0f, 5.0f)
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight)
            active = true

            scene.addChild(this)
        }

        val shell = Box(GLVector(120.0f, 120.0f, 120.0f), insideNormals = true)
        shell.material.doubleSided = true
        shell.material.diffuse = GLVector(0.2f, 0.2f, 0.2f)
        shell.material.specular = GLVector.getNullVector(3)
        shell.material.ambient = GLVector.getNullVector(3)
        scene.addChild(shell)

        val volume = Volume()
        volume.colormap = "jet"
        scene.addChild(volume)

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

        val folder = File(getDemoFilesPath() + "/volumes/box-iso/")
        val files = folder.listFiles()
        val volumes = files.filter { System.err.println(it); it.isFile }.map { it.absolutePath }.sorted()

        var currentVolume = 0
        fun nextVolume(): String {
            val v = volumes[currentVolume % (volumes.size - 1)]
            currentVolume++

            return v
        }

        thread {
            while(!scene.initialized) { Thread.sleep(200) }

            volume.readFromRaw(Paths.get(nextVolume()), replace = true)
            logger.info("Got volume!")
            while(true) {
                volume.rotation.rotateByAngleY(0.001f)
                volume.needsUpdate = true

                Thread.sleep(20)
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
