package graphics.scenery.tests.examples.cluster

import bvv.core.VolumeViewerOptions
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
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
@OptIn(ExperimentalTime::class)
class Lish22Demo: CaveBaseScene("LISH 22 Demo") {
    lateinit var activeObject: Node
    var selectableObjects = ArrayList<Node>()

    val folder = "/Users/hzdr/repos/microscenery/volumes/"

    override fun init() {
        super.init()

        cam.spatial().position = Vector3f(.0f, 0.0f, 10.0f)

        val slicingPlane = SlicingPlane()
        val light = PointLight(radius = 100.0f)
        scene += light



        val mariaPlant = mariaPlant(hub)
        mariaPlant.name = "maria plant"
        mariaPlant.spatial().position = Vector3f(-3.0f, 1.0f, 0.0f)

        val mmBrain = mohammadMouseBrain(hub)
        mmBrain.name = "mohammad mouse brain"
        mmBrain.spatial().position = Vector3f(0.0f, 1.0f, 0.0f)

        val hydra = hydra(hub)
        hydra.name = "hydra"
        hydra.spatial().position = Vector3f(3.0f, 1.0f, 0.0f)

        selectableObjects.addAll(listOf(mariaPlant,mmBrain,hydra))
        selectableObjects.forEach { scene.addChild(it) }

        activeObject = mariaPlant

        val ambient = AmbientLight(0.1f)
        scene += ambient

        val floor = InfinitePlane()
        floor.lineLuminance = 0.05f
        scene += floor

        val croppingHandle = Box(Vector3f(0.1f,0.005f,0.1f))
        cam += croppingHandle


        scene.findByClassname("Volume").forEach {
            val vol = it as Volume
            slicingPlane.addTargetVolume(vol)
            vol.slicingMode = Volume.SlicingMode.None
        }
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


    fun hydra(hub: Hub): Volume {
        val volume = Volume.forNetwork(params = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given(folder+"""Hydra/export.xml"""),
            Volume.VolumeFileSource.VolumeType.SPIM),hub)
        volume.spatial() {
            scale = Vector3f(1.3934f)
        }
        volume.origin = Origin.FrontBottomLeft
        volume.transferFunction = TransferFunction.ramp(0f, 0.8f, 1f)
        volume.colormap = Colormap.get("hot")
        volume.setTransferFunctionRange(111f, 305f)

        var lastTimepoint = 0L
        volume.update += {
            val now = System.currentTimeMillis()
            if (now - lastTimepoint > 250) {
                if (volume.timepointCount - 1 <= volume.currentTimepoint)
                    volume.goToFirstTimepoint()
                else
                    volume.nextTimepoint()

                lastTimepoint = now
            }
        }

        return volume
    }


    fun mariaPlant(hub: Hub): Volume {
        val volume = Volume.forNetwork(params = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given(folder+"""MariaPlant/plant_MMStack_Default.ome.tif"""),
            Volume.VolumeFileSource.VolumeType.TIFF),hub)
        volume.spatial() {
            val openSpimScale15 = Vector3f(.225f, .225f, 1.524f)
            scale = openSpimScale15
        }
        volume.origin = Origin.FrontBottomLeft
        volume.transferFunction = TransferFunction.ramp(0f, 0.8f, 1f)
        volume.colormap = Colormap.get("hot")
        volume.setTransferFunctionRange(400f, 2962f)

        return volume
    }


    fun mohammadMouseBrain(hub: Hub): Volume {
        val volume = Volume.forNetwork(params = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given(folder+"""mohammads_mouse_brain/export.xml"""),
            Volume.VolumeFileSource.VolumeType.SPIM),hub)
        volume.spatial() {
            scale = Vector3f(1.3323542f, 1.3323542f, 4.992066f ) * 0.2f
        }
        volume.origin = Origin.FrontBottomLeft
        volume.transferFunction = TransferFunction.ramp(0f, 0.8f, 1f)
        volume.colormap = Colormap.get("jet")
        volume.setTransferFunctionRange(119f, 388f)

        return volume
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
            Lish22Demo().main()
        }
    }
}
