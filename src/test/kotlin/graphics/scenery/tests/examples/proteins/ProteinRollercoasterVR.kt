package graphics.scenery.tests.examples.proteins

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.flythroughs.ProteinRollercoasterSimple
import graphics.scenery.numerics.Random
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.RibbonDiagram
import org.joml.Vector3f
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Example for usage of VR controllers. Demonstrates the use of custom key bindings on the
 * HMD, the use of intersection testing with scene elements, and more advanced tools.
 *
 * Available Controls:
 * Side buttons alone:  Grab Object
 * Both side buttons together: Move to scale, after selection
 * Right Trigger:       Select to Scale
 * Left Trigger:        Select Party Cube
 * Left A Button:       Options Menu
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
class ProteinRollercoasterVR : SceneryBase(
    ProteinRollercoasterVR::class.java.simpleName,
    windowWidth = 1920, windowHeight = 1200
) {
    private lateinit var hmd: OpenVRHMD
    private lateinit var hullbox: Box
    private val ribbonDiagram = RibbonDiagram(Protein.fromID("3nir"))

    override fun init() {
        hmd = OpenVRHMD(useCompositor = true)

        if (!hmd.initializedAndWorking()) {
            logger.error("This demo is intended to show the use of OpenVR controllers, but no OpenVR-compatible HMD could be initialized.")
            exitProcess(1)
        }

        hub.add(SceneryElement.HMDInput, hmd)

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        renderer?.toggleVR()

        val cam: Camera = DetachedHeadCamera(hmd)
        cam.spatial().position = Vector3f(0.0f, 0.0f, 0.0f)

        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)

        scene.addChild(cam)

        val lights = Light.createLightTetrahedron<PointLight>(spread = 5.0f, radius = 8.0f)
        lights.forEach {
            it.emissionColor = Random.random3DVectorFromRange(0.6f, 1.0f)
            scene.addChild(it)
        }

        hullbox = Box(Vector3f(20.0f, 20.0f, 20.0f), insideNormals = true)
        hullbox.material {
            ambient = Vector3f(0.6f, 0.6f, 0.6f)
            diffuse = Vector3f(0.4f, 0.4f, 0.4f)
            specular = Vector3f(0.0f, 0.0f, 0.0f)
            cullingMode = Material.CullingMode.Front
        }

        scene.addChild(hullbox)

        scene.addChild(ribbonDiagram)
        thread {
            while (!running) {
                Thread.sleep(200)
            }

            hmd.events.onDeviceConnect.add { hmd, device, timestamp ->
                if (device.type == TrackedDeviceType.Controller) {
                    logger.info("Got device ${device.name} at $timestamp")
                    device.model?.let { controller ->
                        // This attaches the model of the controller to the controller's transforms
                        // from the OpenVR/SteamVR system.
                        hmd.attachToNode(device, controller, cam)
                    }
                }
            }
        }
    }

    override fun inputSetup() {
        super.inputSetup()

        // We first grab the default movement actions from scenery's input handler,
        // and re-bind them on the right-hand controller's trackpad or joystick.
        inputHandler?.let { handler ->
            hashMapOf(
                "move_forward" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Up),
                "move_back" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Down),
                "move_left" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Left),
                "move_right" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Right)
            ).forEach { (name, key) ->
                handler.getBehaviour(name)?.let { b ->
                    logger.info("Adding behaviour $name bound to $key to HMD")
                    hmd.addBehaviour(name, b)
                    hmd.addKeyBinding(name, key)
                }
            }
        }


        // Now we add another behaviour for toggling visibility of the boxes
        hmd.addBehaviour("proteinRollercoaster", ProteinRollercoasterSimple(ribbonDiagram) { scene.activeObserver })
        // ...and bind that to the A button of the left-hand controller.
        hmd.addKeyBinding("proteinRollercoaster", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Trigger)

    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ProteinRollercoasterVR().main()
        }
    }
}

