package graphics.scenery.tests.examples.cluster.old

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Cone
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.RibbonDiagram
import graphics.scenery.utils.extensions.minus
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import org.joml.Vector3f
import java.nio.file.Paths
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Example to demonstrate rendering on a cluster. Will display a grid of geometric options,
 * a nice example for testing of the setup's correctness.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ClusterExample: SceneryBase("Clustered Volume Rendering example") {
    var hmd: TrackedStereoGlasses? = null

    override fun init() {
        // Here you can use either a DTrack or VRPN device. Both need to be prefixed
        // with either DTrack: or VRPN: -- for DTrack it's required that multicast mode is activated
        // in DTrack's Configuration -> Network panel. Use the IP set there for multicast here, and use
        // DTrack's device ID here as well. You will also need to set a screen configuration, have
        // a look at CAVEExample.yml for that.
        hmd = hub.add(TrackedStereoGlasses("DTrack:0@224.0.1.1:5001", screenConfig = "UniGeneva.yml"))
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 2560, 1600))

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            //position = Vector3f(.4f, .4f, 1.4f)
            cam.spatial {
                position = Vector3f(.0f, 0f, 0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

        val rowSize = 4f
        val spheres = (0 until (rowSize*rowSize).roundToInt()).map {
            val s = when {
                it % 2 == 0 -> Box(Vector3f(0.2f))
                it % 3 == 0 -> Cone(0.1f, 0.2f, 10)
                else -> Icosphere(0.1f, 2)
            }
            s.spatial {
                position = Vector3f(
                    floor(it / rowSize),
                    (it % rowSize.toInt()).toFloat(),
                    0.0f)
                position = position - Vector3f(
                    (rowSize - 1.0f)/4.0f,
                    (rowSize - 1.0f)/4.0f,
                    0.0f)
            }

            s.material {
                roughness = (it / rowSize)/rowSize
                metallic = (it % rowSize.toInt())/rowSize
                diffuse = Random.random3DVectorFromRange(0.5f, 1.0f)
            }

            scene.addChild(s)
            s
        }

        val protein = RibbonDiagram(Protein.fromID("4kcp"))
        protein.spatial {
            scale = Vector3f(0.02f)
        }
       // scene.addChild(protein)

        val basepath = if(System.getProperty("scenery.master").toBoolean()) {
            // this is the directory on the master
            //"C:/scenery-base/scenery/models/volumes"
            "D:/Ulrik Download/"
        } else {
            // this is the directory on the rendering nodes
            //"S:/scenery/models"
            "D:/Ulrik Download/"

        }

       val volume: BufferedVolume
       val croc = false
       if(croc) {
           volume = Volume.fromPathRaw(Paths.get(basepath + "Croc/104B_08_side1_647_25p.raw"), hub)

           volume.name = "volume"
           volume.colormap = Colormap.get("viridis") // jet, hot, rainbow, plasma, grays
           volume.spatial {
               position = Vector3f(1.0f, 1.0f, 1.0f)
               scale = Vector3f(1.0f, 1.0f, 1.0f)
               rotation = rotation.rotationXYZ(PI.toFloat()/2.0f, PI.toFloat()/4.0f,-(PI.toFloat())/4.0f)
           }
           volume.ds.converterSetups[0].setDisplayRange(150.0, 4000.0)
           //volume.ds.converterSetups[0].setDisplayRange(10.0, 6000.0)

           // for drosophila: probably  volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f)
           volume.transferFunction = TransferFunction.ramp(0.01f, 0.1f)
           //volume.transferFunction.addControlPoint(0.1f, 0.5f)
           scene.addChild(volume)
       } else {
           volume = Volume.fromPathRaw(Paths.get(basepath + "droso-royer-autopilot-transposed"), hub)

           volume.name = "volume"
           volume.colormap = Colormap.get("hot") // jet, hot, rainbow, plasma, grays
           volume.spatial {
               position = Vector3f(1.0f, 1.0f, 1.0f)
               scale = Vector3f(5.0f, 25.0f, 5.0f)
           }
           volume.ds.converterSetups[0].setDisplayRange(20.0, 500.0)
           volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f)
           scene.addChild(volume)
       }

        val lights = Light.createLightTetrahedron<PointLight>(spread = 2.0f, radius = 20.0f)
        lights.forEach { scene.addChild(it) }
        val l = PointLight(5.0f)
        l.spatial().position = Vector3f(0.0f, 2.0f, 2.0f)
        scene.addChild(l)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ClusterExample().main()
        }
    }
}
