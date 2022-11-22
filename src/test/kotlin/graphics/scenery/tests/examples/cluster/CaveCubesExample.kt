package graphics.scenery.tests.examples.cluster

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.DTrackTrackerInput
import graphics.scenery.controls.GamepadButton
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.controls.behaviours.GamepadClickBehaviour
import graphics.scenery.controls.behaviours.GamepadMovementControl
import graphics.scenery.controls.behaviours.GamepadRotationControl
import graphics.scenery.controls.behaviours.withCooldown
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.java.games.input.Component
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
@OptIn(ExperimentalTime::class)
class CaveCubesExample: SceneryBase("Bile Canaliculi example", wantREPL = true) {
    var hmd: TrackedStereoGlasses? = null
    lateinit var activeObject: Node
    lateinit var selectableObjects: List<Node>

    override fun init() {
        val tsg = TrackedStereoGlasses("DTrack:body-0@224.0.1.1:5001", screenConfig = "CAVEExample.yml")
        hmd = hub.add(tsg)

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 512, 320))

        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            networkID = -5
            spatial {
                position = Vector3f(.0f, 0.0f, 10.0f)
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


        val lights = Light.createLightTetrahedron<PointLight>(spread = 50.0f, intensity = 10.0f, radius = 100.0f)
        lights.forEach { scene.addChild(it) }

        val retina = Volume.forNetwork(params = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given("""E:\datasets\retina_test2\retina_53_1024_1024.tif"""),
            Volume.VolumeFileSource.VolumeType.TIFF),hub)
        retina.colormap = Colormap.get("hot")
        retina.transferFunction = TransferFunction.ramp(0.01f, 0.6f)
        retina.setTransferFunctionRange(200.0f, 36000.0f)
        retina.origin = Origin.FrontBottomLeft
        retina.spatial {
            scale = Vector3f(2.0f,5.0f,10.0f)
        }
        retina.name = "Mouse retina"
        scene.addChild(retina)

        val drosophila = Volume.forNetwork(params = Volume.VolumeFileSource(
            Volume.VolumeFileSource.VolumePath.Given("""E:\datasets\droso-royer-autopilot-transposed-bdv\export-norange.xml"""),
            Volume.VolumeFileSource.VolumeType.SPIM),hub)

        drosophila.colormap = Colormap.get("hot")
        drosophila.transferFunction = TransferFunction.ramp(0.01f, 0.6f)
        drosophila.setTransferFunctionRange(10.0f, 1200.0f)
        drosophila.origin = Origin.FrontBottomLeft
        drosophila.spatial {
            scale = Vector3f(0.1f,10.0f,0.1f)
            position = Vector3f(0.0f, 0.0f, -15.0f)
        }
        drosophila.name = "Drosophila timelapse"
        scene.addChild(drosophila)

        val bileScene = RichNode()
        val bile = RichNode()
        val canaliculi = Mesh.forNetwork("E:/datasets/bile/bile-canaliculi.obj", true, hub)
        canaliculi.spatial {
            scale = Vector3f(0.05f)
            position = Vector3f(-160.0f, -120.0f, 10.0f)
        }
        canaliculi.material {
            diffuse = Vector3f(0.5f, 0.7f, 0.1f)
        }
        bile.addChild(canaliculi)

        val nuclei = Mesh.forNetwork("E:/datasets/bile/bile-nuclei.obj", true, hub)
        nuclei.spatial {
            scale = Vector3f(0.05f)
            position = Vector3f(-160.0f, -120.0f, 10.0f)
        }
        nuclei.material {
            diffuse = Vector3f(0.8f, 0.8f, 0.8f)
        }
        bile.addChild(nuclei)

        val sinusoidal = Mesh.forNetwork("E:/datasets/bile/bile-sinus.obj", true, hub)
        sinusoidal.spatial {
            scale = Vector3f(0.05f)
            position = Vector3f(-160.0f, -120.0f, 10.0f)
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

        activeObject = bileScene
        selectableObjects = listOf(bileScene, drosophila, retina)
    }

    override fun inputSetup() {
        val inputHandler = (hub.get(SceneryElement.Input) as? InputHandler) ?: return

        val selectObjects = object : GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                withCooldown(200.milliseconds) {
                    // finds the currently active protein, un-highlights it
                    activeObject.children.forEach {
                        if (it is BoundingGrid) {
                            it.gridColor = Vector3f(0.0f, 0.0f, 0.0f)
                        }
                    }

                    // selects the new active protein
                    val nextIndex = (selectableObjects.indexOf(activeObject)+1) % (selectableObjects.size)
                    activeObject = selectableObjects[nextIndex]
                    logger.info("New object is $activeObject ($nextIndex)")

                    scene.findObserver()?.showMessage("Now controlling ${activeObject.name}",
                        distance = 1.0f,
                        offset = Vector2f(0.0f, -0.25f),
                        transparent = true
                    )

                    // highlights the newly active protein
                    activeObject.children.forEach {
                        if (it is BoundingGrid) {
                            it.gridColor = Vector3f(1.0f, 0.0f, 0.0f)
                        }
                    }
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
                    spatial.scale = spatial.scale * 1.2f
                    logger.info("Scaling up $activeObject")
                }
            }
        }
            called "scale_up"
            boundTo GamepadButton.Button4)

        inputHandler += (object: GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                activeObject.spatialOrNull()?.let { spatial ->
                    spatial.scale = spatial.scale / 1.2f
                    logger.info("Scaling down $activeObject")
                }
            }
        }
            called "scale_down"
            boundTo GamepadButton.Button5)

        inputHandler += (object: GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                activeObject.spatialOrNull()?.let { spatial ->
                    spatial.scale = Vector3f(1.0f)
                    spatial.rotation = Quaternionf()

                    scene.findObserver()?.showMessage("Resetting ${activeObject.name}",
                        distance = 1.0f,
                        offset = Vector2f(0.0f, -0.25f),
                        transparent = true
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
            CaveCubesExample().main()
        }
    }
}
