package graphics.scenery.tests.examples.cluster

import graphics.scenery.*
import graphics.scenery.controls.*
import graphics.scenery.controls.behaviours.GamepadClickBehaviour
import graphics.scenery.controls.behaviours.GamepadMovementControl
import graphics.scenery.controls.behaviours.GamepadRotationControl
import graphics.scenery.controls.behaviours.withCooldown
import graphics.scenery.primitives.InfinitePlane
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.RibbonDiagram
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.*
import net.java.games.input.Component
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.File
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
@OptIn(ExperimentalTime::class)
class CaveDemoWithSlicing: CaveBaseScene("uff") {
    lateinit var activeObject: Node
    var selectableObjects = ArrayList<Node>()

    override fun init() {
        super.init()

        cam.spatial().position = Vector3f(.0f, 0.0f, 10.0f)

        val slicingPlane = SlicingPlane()
        val light = PointLight(radius = 100.0f)
        scene += light

        val retina = Volume.forNetwork(params = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given("""E:\datasets\retina_test2\retina_53_1024_1024.tif"""),
            Volume.VolumeFileSource.VolumeType.TIFF),hub)
        retina.colormap = Colormap.get("jet")
        retina.transferFunction = TransferFunction.ramp(0.01f, 0.6f)
        retina.setTransferFunctionRange(200.0f, 36000.0f)
        retina.slicingMode = Volume.SlicingMode.None
        slicingPlane.addTargetVolume(retina)
        retina.origin = Origin.Center
        retina.spatial {
            scale = Vector3f(2.0f, 2.0f, 6.0f)
            position = Vector3f(5.0f, 1.0f, 0.0f)
        }
        retina.name = "Mouse retina"
        retina.loadTransferFunctionFromFile(File("""E:\datasets\retina_test2\transferFunction"""))
        scene.addChild(retina)
        selectableObjects.add(retina)

        val drosophila = Volume.forNetwork(params = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given("""E:\datasets\droso-royer-autopilot-transposed-bdv\export-norange.xml"""),
            Volume.VolumeFileSource.VolumeType.SPIM),hub)

        drosophila.colormap = Colormap.get("hot")
        drosophila.transferFunction = TransferFunction.ramp(0.01f, 0.6f)
        drosophila.setTransferFunctionRange(20.0f, 1200.0f)
        drosophila.origin = Origin.Center
        drosophila.multiResolutionLevelLimits = 1 to 2
        drosophila.spatial {
            scale = Vector3f(1.0f, 5.0f, 1.0f)
            position = Vector3f(10.0f, 1.0f, 0.0f)
        }
        drosophila.name = "Drosophila timelapse"
        drosophila.slicingMode = Volume.SlicingMode.Cropping
        slicingPlane.addTargetVolume(drosophila)
        scene.addChild(drosophila)
        selectableObjects.add(drosophila)

//        TransferFunctionEditor.showTFFrame(drosophila)

        val bile = initBile()

        val ferry = RichNode()
        ferry.name = "FERRY complex"
        ferry.spatial().position = Vector3f(-5.0f, 1.0f, 0.0f)
        val protein = RibbonDiagram(Protein.fromID("7nd2"))
        protein.spatial().scale = Vector3f(0.01f)
        ferry += protein

        val cryoEM = Volume.forNetwork(params = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given("""E:\datasets\ferry\emd_12273_cropped.tif"""),
            Volume.VolumeFileSource.VolumeType.TIFF), hub)
        cryoEM.spatial().apply {
            scale = Vector3f(10.0f)
            position = Vector3f(0.0f, 0.0f, 0.05f)
        }
        slicingPlane.addTargetVolume(cryoEM)
        cryoEM.slicingMode = Volume.SlicingMode.Cropping
        cryoEM.colormap = Colormap.get("grays")
        cryoEM.transferFunction = TransferFunction.ramp(0.01f, 1f, 1.0f)
        ferry += cryoEM
        scene += ferry
        selectableObjects.add(ferry)

//        TransferFunctionEditor.showTFFrame(cryoEM)

        val ambient = AmbientLight(0.1f)
        scene += ambient

        val floor = InfinitePlane()
        floor.lineLuminance = 0.05f
        scene += floor

        activeObject = bile

        val croppingHandle = Box(Vector3f(0.1f,0.005f,0.1f))
        cam += croppingHandle


//        scene.findByClassname("Volume").forEach {
//            val vol = it as Volume
//            slicingPlane.addTargetVolume(vol)
//            vol.slicingMode = Volume.SlicingMode.Cropping
//        }
        croppingHandle.addChild(slicingPlane)
        croppingHandle.update += {
            val headPose = (hmd?.tracker as? DTrackTrackerInput)?.getPose()
            var headPos = Vector3f()
            headPose!!.getTranslation(headPos)
            headPos *= (-1.0f)

            val controllerPose = (hmd?.tracker as? DTrackTrackerInput)?.getPose(TrackedDeviceType.Controller)
            controllerPose?.firstOrNull()?.pose?.let {
                val p = Vector3f()
                val aa = AxisAngle4f()
                it.getTranslation(p)
                it.getRotation(aa)
                val rot = Quaternionf(aa)

                val diff = headPos - Vector3f(p.x, p.y, p.z)
                croppingHandle.spatial().position = Vector3f(-diff.x, -diff.y, diff.z)
                croppingHandle.spatial().rotation = rot
            }
        }
    }

    private fun initBile(): RichNode {
        val bile = RichNode()
        bile.apply {
            val canaliculi = Mesh.forNetwork("E:/datasets/bile/bile-canaliculi.obj", true, hub)
            canaliculi.spatial {
                scale = Vector3f(0.005f)
                origin = Origin.Center
            }
            canaliculi.material {
                diffuse = Vector3f(0.5f, 0.7f, 0.1f)
            }
            addChild(canaliculi)

            val nuclei = Mesh.forNetwork("E:/datasets/bile/bile-nuclei.obj", true, hub)
            nuclei.spatial {
                scale = Vector3f(0.005f)
                origin = Origin.Center
            }
            nuclei.material {
                diffuse = Vector3f(0.8f, 0.8f, 0.8f)
            }
            addChild(nuclei)

            val sinusoidal = Mesh.forNetwork("E:/datasets/bile/bile-sinus.obj", true, hub)
            sinusoidal.spatial {
                scale = Vector3f(0.005f)
                origin = Origin.Center
            }
            sinusoidal.material {
                ambient = Vector3f(0.1f, 0.0f, 0.0f)
                diffuse = Vector3f(0.4f, 0.0f, 0.02f)
                specular = Vector3f(0.05f, 0f, 0f)
            }
            addChild(sinusoidal)

            name = "Bile Network"
        }
        bile.spatial().position = Vector3f(0.0f, 2.0f, 0.0f)
        scene += bile
        selectableObjects.add(bile)
        return bile
    }

    override fun inputSetup() {
        super.inputSetup()

        val inputHandler = (hub.get(SceneryElement.Input) as? InputHandler) ?: return
        var playerThread: Thread? = null

        val selectObjects = object : GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                withCooldown(200.milliseconds) {
                    selectableObjects.forEach { it.visible = false }
                    // selects the new active protein
                    val nextIndex = (selectableObjects.indexOf(activeObject)+1) % (selectableObjects.size)
                    activeObject = selectableObjects[nextIndex]
                    activeObject.visible = true
                    logger.info("New object is $activeObject ($nextIndex)")

                    scene.findObserver()?.showMessage("Now controlling ${activeObject.name}",
                        distance = 1.0f,
                        transparent = true,
                        centered = true
                    )
                }
            }
        }

        // toggles the active protein by pressing 1 on the keyboard, or the B key
        // on the gamepad.
        inputHandler += (selectObjects
            called "select_objects"
            boundTo GamepadButton.Button1)

        // removes the default second-stick camera look-around for the gamepad
        inputHandler -= "gamepad_camera_control"
        // replace default movement control to also include z movement
        inputHandler.behaviourMap.put("gamepad_movement_control", GamepadMovementControl(listOf(Component.Identifier.Axis.X, Component.Identifier.Axis.Y, Component.Identifier.Axis.Z)) { scene.findObserver() })
        // adds a new behaviour for rotating the [activeProtein], RX and RY are the rotation
        // axis on the Xbox Wireless controller. For other controllers, different axis may
        // have to be used. Gamepad movement and rotation behaviours are always active,
        // hence the [GamepadButton.AlwaysActive] key binding.
        inputHandler += (GamepadRotationControl(listOf(Component.Identifier.Axis.RY, Component.Identifier.Axis.RX), 0.5f) { activeObject }
            called "object_rotation"
            boundTo GamepadButton.AlwaysActive)

        inputHandler += (object: GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                activeObject.spatialOrNull()?.let { spatial ->
                    spatial.scale = spatial.scale * 1.05f
                    logger.info("Scaling up $activeObject")
                }
            }
        }
            called "scale_up"
            boundTo GamepadButton.Button5)

        inputHandler += (object: GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                if(!activeObject.name.startsWith("Drosophila")) {
                    return
                }

                playerThread = if(playerThread == null) {
                    thread {
                        while(!Thread.interrupted()) {
                            val vol = (scene.find("Drosophila timelapse") as? Volume) ?: continue
                            if(vol.currentTimepoint == vol.timepointCount -1) {
                                vol.goToFirstTimepoint()
                            } else {
                                vol.nextTimepoint()
                            }

                            Thread.sleep(100)
                        }
                    }
                } else {
                    (playerThread as? Thread)?.interrupt()
                    null
                }
            }
        }
            called "play_pause_volume"
            boundTo GamepadButton.Button2)

        inputHandler += (object: GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                activeObject.spatialOrNull()?.let { spatial ->
                    spatial.scale = spatial.scale / 1.05f
                    logger.info("Scaling down $activeObject")
                }
            }
        }
            called "scale_down"
            boundTo GamepadButton.Button4)

        inputHandler += (object: GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                activeObject.spatialOrNull()?.let { spatial ->
                    if(activeObject.name.startsWith("Drosophila")) {
                        spatial.scale = Vector3f(1.0f, 5.0f, 1.0f)
                    } else {
                        spatial.scale = Vector3f(1.0f)
                    }
                    spatial.rotation = Quaternionf()

                    scene.findObserver()?.showMessage("Resetting ${activeObject.name}",
                        distance = 1.0f,
                        transparent = true,
                        centered = true
                    )
                }
            }
        }
            called "reset_scaling_and_rotation"
            boundTo GamepadButton.Button0)

        inputHandler += (GamepadMovementControl(listOf(Component.Identifier.Axis.Z)) { scene.findObserver() }
            called "vertical_movement"
            boundTo GamepadButton.AlwaysActive)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CaveDemoWithSlicing().main()
        }
    }
}
