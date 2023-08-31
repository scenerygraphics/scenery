package graphics.scenery.tests.examples.advanced

import org.joml.*
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.*
import graphics.scenery.controls.behaviours.VRSelectionWheel.Companion.toActions
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.RibbonDiagram
import graphics.scenery.utils.Wiggler
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * This is an example for the default visualization of a ribbon diagram in VR. There is a color vector implemented so that
 * the rainbow spectrum runs along the backbone (starting with red at the n terminal and ending with purple at the
 * c terminal).
 * If you want to change the visualized protein, enter the pdb entry into the Protein.fromID(//your entry goes here).
 * If you want the secondary structures to have a different color, go to "RibbonExampleSecondaryStructures".
 *
 * @author Justin Buerger <burger@mpi-cbg.de>
 */
class RainbowRibbonExampleVR: SceneryBase(
        RainbowRibbonExampleVR::class.java.simpleName,
        windowWidth = 1920, windowHeight = 1200
    ) {
        private lateinit var hmd: OpenVRHMD
        private lateinit var boxes: List<Node>
        private lateinit var hullbox: Box
        private var leftControllerPushes = true
        private var selectionStorage: Node? = null

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
                it.emissionColor = Random.random3DVectorFromRange(0.8f, 1.0f)
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

            val protein = Protein.fromID("2zzt")

            val ribbon = RibbonDiagram(protein)
            ribbon.addAttribute(Grabable::class.java, Grabable())
            ribbon.spatial { scale = Vector3f(0.1f) }
            scene.addChild(ribbon)

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

            // initialize touching, grabbing  and pressing(clicking) of objects
            VRTouch.createAndSet(scene, hmd, listOf(TrackerRole.RightHand, TrackerRole.LeftHand), true)
            VRGrab.createAndSet(
                scene,
                hmd,
                listOf(OpenVRHMD.OpenVRButton.Side),
                listOf(TrackerRole.LeftHand, TrackerRole.RightHand)
            )
            VRPress.createAndSet(
                scene,
                hmd,
                listOf(OpenVRHMD.OpenVRButton.Trigger, OpenVRHMD.OpenVRButton.A),
                listOf(TrackerRole.LeftHand, TrackerRole.RightHand)
            )

            // hold both side buttons to scale the selection
            VRScale.createAndSet(hmd, OpenVRHMD.OpenVRButton.Side) {
                selectionStorage?.ifSpatial { scale *= Vector3f(it) }
            }
        }


        companion object {
            @JvmStatic
            fun main(args: Array<String>) {
                RainbowRibbonExampleVR().main()
            }
        }
    }
