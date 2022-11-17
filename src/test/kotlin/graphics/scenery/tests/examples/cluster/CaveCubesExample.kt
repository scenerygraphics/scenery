package graphics.scenery.tests.examples.cluster

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.DTrackTrackerInput
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.Colormap
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
        val tsg = TrackedStereoGlasses("DTrack:body-0@224.0.1.1:5001", screenConfig = "CAVEExample.yml")
        hmd = hub.add(tsg)
        val tracker = tsg.tracker as DTrackTrackerInput

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 512, 320))

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            networkID = -5
            spatial {
                position = Vector3f(.0f, 0.0f, 0.0f)
                networkID = -7
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
            Volume.VolumeFileSource.VolumePath.Given("""E:\datasets\retina_test2\retina_53_1024_1024.tif"""),
            Volume.VolumeFileSource.VolumeType.TIFF),hub)
        scene.addChild(volume)
        volume.colormap = Colormap.get("hot")
        volume.transferFunction = TransferFunction.ramp(0.01f, 0.6f)
        volume.setTransferFunctionRange(200.0f, 36000.0f)
        volume.origin = Origin.FrontBottomLeft
        volume.spatial {
            scale = Vector3f(2.0f,5.0f,10.0f)
        }

        val dwrap = RichNode()
        val drosophila = Volume.forNetwork(params = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given("""E:\datasets\droso-royer-autopilot-transposed-bdv\export-norange.xml"""),
            Volume.VolumeFileSource.VolumeType.SPIM),hub)
        dwrap.addChild(drosophila)
        drosophila.colormap = Colormap.get("hot")
        drosophila.transferFunction = TransferFunction.ramp(0.01f, 0.6f)
        drosophila.setTransferFunctionRange(10.0f, 1200.0f)
        drosophila.origin = Origin.FrontBottomLeft
        drosophila.spatial {
            scale = Vector3f(0.1f,10.0f,0.1f)
            position = Vector3f(0.0f, 0.0f, -15.0f)
        }
        scene.addChild(dwrap)

        val bileScene = RichNode()
        val bile = RichNode()
        val canaliculi = Mesh.forNetwork("E:/datasets/bile/bile-canaliculi.obj", true, hub)
        canaliculi.spatial {
            scale = Vector3f(0.1f, 0.1f, 0.1f)
            position = Vector3f(-80.0f, -60.0f, 10.0f)
        }
        canaliculi.material {
            diffuse = Vector3f(0.5f, 0.7f, 0.1f)
        }
        bile.addChild(canaliculi)

        val nuclei = Mesh.forNetwork("E:/datasets/bile/bile-nuclei.obj", true, hub)
        nuclei.spatial {
            scale = Vector3f(0.1f, 0.1f, 0.1f)
            position = Vector3f(-80.0f, -60.0f, 10.0f)
        }
        nuclei.material {
            diffuse = Vector3f(0.8f, 0.8f, 0.8f)
        }
        bile.addChild(nuclei)

        val sinusoidal = Mesh.forNetwork("E:/datasets/bile/bile-sinus.obj", true, hub)
        sinusoidal.spatial {
            scale = Vector3f(0.1f, 0.1f, 0.1f)
            position = Vector3f(-80.0f, -60.0f, 10.0f)
        }
        sinusoidal.material {
            ambient = Vector3f(0.1f, 0.0f, 0.0f)
            diffuse = Vector3f(0.4f, 0.0f, 0.02f)
            specular = Vector3f(0.05f, 0f, 0f)
        }
        bile.addChild(sinusoidal)

        bileScene.addChild(bile)
        bileScene.name = "Bile Network"
        scene.addChild(bileScene)

        listOf(Vector3f(0f,0f,-5f),
            Vector3f(5f,0f,0f),
            Vector3f(-5f,0f,0f),
            Vector3f(0f,0f,5f),
            //Vector3f(0f,0f,-1f)
            ).forEach {
                scene.addChild(Box(Vector3f(0.1f)).apply {
                    spatial().position = it
                })
            }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CaveCubesExample().main()
        }
    }
}
