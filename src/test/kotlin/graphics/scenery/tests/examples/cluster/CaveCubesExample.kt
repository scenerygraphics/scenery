package graphics.scenery.tests.examples.cluster

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import org.joml.Vector3f
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.io.path.absolute

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class CaveCubesExample: SceneryBase("Bile Canaliculi example", wantREPL = true) {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        hmd = hub.add(TrackedStereoGlasses("DTrack:body-0@224.0.1.1:5001", screenConfig = "CAVEExample.yml"))

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 512, 320))

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            networkID = -5
            spatial {
                position = Vector3f(.0f, -0.4f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            scene.addChild(this)
        }

        val shell = Box(Vector3f(120.0f, 120.0f, 120.0f), insideNormals = true)
        shell.material {
            cullingMode = Material.CullingMode.Front
            diffuse = Vector3f(0.0f, 0.0f, 0.0f)
            specular = Vector3f(0.0f)
            ambient = Vector3f(0.0f)
        }
        scene.addChild(shell)

        val lights = (0..4).map {
            PointLight(radius = 200.0f)
        }

        val tetrahedron = listOf(
            Vector3f(1.0f, 0f, -1.0f/Math.sqrt(2.0).toFloat()),
            Vector3f(-1.0f,0f,-1.0f/Math.sqrt(2.0).toFloat()),
            Vector3f(0.0f,1.0f,1.0f/Math.sqrt(2.0).toFloat()),
            Vector3f(0.0f,-1.0f,1.0f/Math.sqrt(2.0).toFloat()))

        tetrahedron.mapIndexed { i, position ->
            lights[i].spatial().position = position * 50.0f
            lights[i].emissionColor = Vector3f(1.0f, 0.5f,0.3f)//Random.random3DVectorFromRange(0.2f, 0.8f)
            lights[i].intensity = 20.2f
            scene.addChild(lights[i])
        }

        val volume = Volume.forNetwork(params = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given("""Z:\datasets\droso-royer-autopilot-transposed-bdv\export-norange.xml"""),
            //Volume.VolumeFileSource.VolumePath.Settings(),
            Volume.VolumeFileSource.VolumeType.SPIM),hub)
        scene.addChild(volume)
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.8f)
        volume.setTransferFunctionRange(10.0f, 500.0f)
        volume.multiResolutionLevelLimits = 0 to 1
        volume.spatial {
            scale = Vector3f(0.1f,2.0f,0.1f)
            needsUpdate = true
        }

        listOf(Vector3f(0f,0f,-5f),
            Vector3f(5f,0f,0f),
            Vector3f(-5f,0f,0f),
            Vector3f(0f,0f,5f))
            .forEach {
                scene.addChild(Box(Vector3f(0.1f)).apply {
                    spatial().position = it
                })
            }
    }

    companion object {
        private val logger by LazyLogger()

        @JvmStatic
        fun main(args: Array<String>) {
            CaveCubesExample().main()
        }
    }
}
