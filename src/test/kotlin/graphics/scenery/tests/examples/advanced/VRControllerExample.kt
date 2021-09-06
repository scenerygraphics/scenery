package graphics.scenery.tests.examples.advanced

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.*
import graphics.scenery.numerics.Random
import graphics.scenery.utils.Wiggler
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Vector3f
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Example for usage of VR controllers. Demonstrates the use of custom key bindings on the
 * HMD, the use of intersection testing with scene elements, and more advanced tools.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VRControllerExample : SceneryBase(
    VRControllerExample::class.java.simpleName,
    windowWidth = 1920, windowHeight = 1200
) {
    private lateinit var hmd: OpenVRHMD
    private lateinit var boxes: List<Node>
    private lateinit var hullbox: Box

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

        boxes = (0..10).map {
            val obj = Box(Vector3f(0.1f, 0.1f, 0.1f))
            obj.spatial {
                position = Vector3f(-1.0f + (it + 1) * 0.2f, 1.0f, -0.5f)
            }
            obj.addAttribute(Grabable::class.java, Grabable())
            obj.addAttribute(Selectable::class.java, Selectable())
            obj
        }

        boxes.forEach { scene.addChild(it) }

        /** Box with rotated parent to debug grabbing
        val pivot = RichNode()
        pivot.spatial().rotation.rotateLocalY(Math.PI.toFloat())
        scene.addChild(pivot)

        val longBox = Box(Vector3f(0.1f, 0.2f, 0.1f))
        longBox.spatial {
            position = Vector3f(-0.5f, 1.0f, 0f)
        }
        longBox.addAttribute(Grabable::class.java, Grabable())
        pivot.addChild(longBox)
        */


        val lights = Light.createLightTetrahedron<PointLight>(spread = 5.0f, radius = 8.0f)
        lights.forEach {
            it.emissionColor = Random.random3DVectorFromRange(0.0f, 1.0f)
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

                        // This update routine is called every time the position of the left controller
                        // updates, and checks for intersections with any of the boxes. If there is
                        // an intersection with a box, that box is slightly nudged in the direction
                        // of the controller's velocity.
                        if (device.role == TrackerRole.LeftHand) {
                            controller.update.add {
                                boxes.forEach { it.materialOrNull()?.diffuse = Vector3f(0.9f, 0.5f, 0.5f) }
                                boxes.filter { box ->
                                    controller.children.first().spatialOrNull()?.intersects(box) ?: false
                                }.forEach { box ->
                                    box.ifMaterial {
                                        diffuse = Vector3f(1.0f, 0.0f, 0.0f)
                                    }
                                    if (device.role == TrackerRole.LeftHand) {
                                        box.ifSpatial {
                                            position = (device.velocity ?: Vector3f(0.0f)) * 0.05f + position
                                        }
                                    }
                                    (hmd as? OpenVRHMD)?.vibrate(device)
                                }
                            }
                        }
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

        /** example of click input
        // Now we add another behaviour for toggling visibility of the boxes
        hmd.addBehaviour("toggle_boxes", ClickBehaviour { _, _ ->
        boxes.forEach { it.visible = !it.visible }
        logger.info("Boxes visible: ${boxes.first().visible}")
        })
        // ...and bind that to the A button of the left-hand controller.
        hmd.addKeyBinding("toggle_boxes", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.A)
         */

        VRGrab.createAndSet(scene, hmd, listOf(OpenVRHMD.OpenVRButton.Side), listOf(TrackerRole.RightHand))
        val selectionWithStorageAndScaling = true //set false for selection with action
        if (selectionWithStorageAndScaling) {
            val selectionStorage =
                VRSelect.createAndSetWithStorage(
                    scene,
                    hmd,
                    listOf(OpenVRHMD.OpenVRButton.Side),
                    listOf(TrackerRole.LeftHand)
                )
            VRScale.createAndSet(hmd, OpenVRHMD.OpenVRButton.Side) {
                selectionStorage.selected?.ifSpatial { scale *= Vector3f(it) }
            }
        } else {
            VRSelect.createAndSetWithAction(scene,
                hmd,
                listOf(OpenVRHMD.OpenVRButton.Side),
                listOf(TrackerRole.LeftHand),
                { n ->
                    val w = Wiggler(n.spatialOrNull()!!, 1.0f)
                    thread {
                        sleep(2 * 1000)
                        w.deativate()
                    }
                })
        }
        VRSelectionWheel.createAndSet(scene, hmd, { hmd.getPosition() },
            listOf(OpenVRHMD.OpenVRButton.A), listOf(TrackerRole.LeftHand),
            listOf(
                "Toggle Shell" to {
                    hullbox.visible = !hullbox.visible
                    logger.info("Hull visible: ${hullbox.visible}")
                },
                "Toggle Boxes" to {
                    boxes.forEach { it.visible = !it.visible }
                    logger.info("Boxes visible: ${boxes.first().visible}")
                },
                "test" to { print("test") }
            ))
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VRControllerExample().main()
        }
    }
}
