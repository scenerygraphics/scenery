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
class VolumeExample: SceneryBase("Volume Rendering example", 1280, 720) {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
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
        volume.trangemax = 1000.0f
        scene.addChild(volume)

        val v2 = Volume()
        v2.colormap = "viridis"
        v2.trangemax = 1000.0f
        v2.position = GLVector(1.0f, 0.0f, -2.0f)
        scene.addChild(v2)

        val b = Box()
        b.position = GLVector(-1.0f, 0.0f, 0.0f)
        scene.addChild(b)

        val b2 = Box()
        b2.position = GLVector(2.0f, 0.0f, 0.0f)
        scene.addChild(b2)

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

            val v = nextVolume()
            volume.readFromRaw(Paths.get(v), replace = true)
            v2.readFromRaw(Paths.get(v), replace = true)

            logger.info("Got volume!")
            while(true) {
//                volume.rotation.rotateByAngleY(0.001f)
                volume.needsUpdate = true
                v2.needsUpdate = true

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
