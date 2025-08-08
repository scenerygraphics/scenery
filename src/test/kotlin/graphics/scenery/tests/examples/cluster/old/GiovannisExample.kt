package graphics.scenery.tests.examples.cluster.old

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.controls.behaviours.GamepadClickBehaviour
import graphics.scenery.controls.behaviours.GamepadRotationControl
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.RibbonDiagram
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.java.games.input.Component
import org.joml.Vector3f
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.math.PI

/**
 * Example to demonstrate rendering on a cluster. Will display a grid of geometric options,
 * a nice example for testing of the setup's correctness.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class GiovannisExample: SceneryBase("Clustered Volume Rendering example, Giovanni style") {
    var hmd: TrackedStereoGlasses? = null
    var publishedNodes = ArrayList<Node>()

    lateinit var volume: BufferedVolume

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
            position = Vector3f(.0f, 0f, 0f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }

//        val rowSize = 4f
//        val spheres = (0 until (rowSize*rowSize).roundToInt()).map {
//            val s = when {
//                it % 2 == 0 -> Box(Vector3f(0.2f))
//                it % 3 == 0 -> Cone(0.1f, 0.2f, 10)
//                else -> Icosphere(0.1f, 2)
//            }
//            s.position = Vector3f(
//                floor(it / rowSize),
//                (it % rowSize.toInt()).toFloat(),
//                0.0f)
//            s.position = s.position - Vector3f(
//                (rowSize - 1.0f)/4.0f,
//                (rowSize - 1.0f)/4.0f,
//                0.0f)
//
//            s.material.roughness = (it / rowSize)/rowSize
//            s.material.metallic = (it % rowSize.toInt())/rowSize
//            s.material.diffuse = Random.random3DVectorFromRange(0.5f, 1.0f)
//
//            scene.addChild(s)
//            s
//        }

        val hull = Box.hulledBox(Vector3f(10.0f))
        hull.spatial().position = Vector3f(0.0f,4.0f,0.0f)
        hull.material().diffuse = Vector3f(0.1f)
      //  scene.addChild(hull)

        val protein = RibbonDiagram(Protein.fromID("4kcp"))
        protein.spatial().scale = Vector3f(0.02f)
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


        val croc = true
        if(croc) {
            volume = Volume.fromPathRaw(Paths.get(basepath + "Croc/104B_08_side1_647_25p.raw"), hub)

            volume.name = "volume"
            volume.colormap = Colormap.get("hot") // jet, hot, rainbow, plasma, grays
            volume.spatial {
                position = Vector3f(1.0f, 1.0f, 1.0f)
                scale = Vector3f(2.0f, 2.0f, 2.0f)
            }
            //volume.rotation = volume.rotation.rotationZ(PI.toFloat()/2.0f)
            //volume.rotation = volume.rotation.rotationX(PI.toFloat())
            volume.spatial().rotation = volume.rotation.rotationXYZ(PI.toFloat()/2.0f, PI.toFloat()/4.0f,-(PI.toFloat())/4.0f)
            volume.ds.converterSetups[0].setDisplayRange(150.0, 4000.0)
            //volume.ds.converterSetups[0].setDisplayRange(10.0, 6000.0)

            // for drosophila: probably  volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f)
            volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f)
            //volume.transferFunction.addControlPoint(0.1f, 0.5f)
            scene.addChild(volume)
            volume.origin = Origin.FrontBottomLeft
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
            volume.origin = Origin.Center
        }



        val lights = Light.createLightTetrahedron<PointLight>(spread = 5.0f, radius = 20.0f)
        lights.forEach { scene.addChild(it) }
        val l = PointLight(5.0f)
        l.spatial().position = Vector3f(0.0f, 2.0f, 2.0f)
        scene.addChild(l)

        /*publishedNodes.add(cam)
        spheres.forEach { publishedNodes.add(it) }
        lights.forEach { publishedNodes.add(it) }
        //publishedNodes.add(protein)
        publishedNodes.add(l)
        publishedNodes.add(volume)

        val publisher = hub.get<NodePublisher>(SceneryElement.NodePublisher)
        val subscriber = hub.get<NodeSubscriber>(SceneryElement.NodeSubscriber)

        publishedNodes.forEachIndexed { index, node ->
            publisher?.nodes?.put(13337 + index, node)
            subscriber?.nodes?.put(13337 + index, node)
        }
         */

//        if(settings.get<Boolean>("master")) {
//            thread {
//                while(running) {
//                    volume.goToTimepoint(volume.currentTimepoint + 1)
//                    Thread.sleep(100)
//                }
//            }
//        }

    }

    @Volatile var loopRunning = false
    override fun inputSetup() {
        val inputHandler = inputHandler ?:return
        val loop = object: GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                if(loopRunning) return

                thread {
                    loopRunning = true
                    (0 until volume.timepointCount).forEach { i ->
                        logger.info("Going to timepoint $i")
                        volume.goToTimepoint(i)
                        Thread.sleep(100)
                    }
                    loopRunning = false
                }
            }
        }

        inputHandler.addBehaviour("loop_volume", loop)
        inputHandler.addKeyBinding("loop_volume", "shift L")

        inputHandler += (loop called "loop_volume" boundTo "1")

        logger.info("Registered loop behaviour")

        // removes the default second-stick camera look-around for the gamepad
        inputHandler -= "gamepad_camera_control"
        // adds a new behaviour for rotating the [activeProtein], RX and RY are the rotation
        // axis on the Xbox Wireless controller. For other controllers, different axis may
        // have to be used. Gamepad movement and rotation behaviours are always active,
        // the key binding is only added for compatibility reasons.
        inputHandler += (GamepadRotationControl(listOf(
            Component.Identifier.Axis.RX,
            Component.Identifier.Axis.RY), 0.03f) { volume }
            called "volume_rotation"
            boundTo "B")

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GiovannisExample().main()
        }
    }
}
