package graphics.scenery.tests.examples.advanced

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.*
import graphics.scenery.controls.behaviours.VRSelectionWheel.Companion.toActions
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
 * Contents:
 * - Boxes to select and scale and to grab and move
 * - A grabable spray can to the left
 * - A touchable party sphere to the right
 *
 * Available Controls:
 * Side buttons alone:  Grab Object
 * Both side buttons together: Move to scale, after selection
 * Right Trigger:       Select to Scale
 * Left Trigger:        Select to Party first, then Scale
 * Left A Button:       Options Menu
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
class VRControllerExample : SceneryBase(
    VRControllerExample::class.java.simpleName,
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

        boxes = (0..10).map {
            val obj = Box(Vector3f(0.1f, 0.1f, 0.1f))
            obj.spatial {
                position = Vector3f(-1.0f + (it + 1) * 0.2f, 1.0f, -0.5f)
            }
            obj.materialOrNull().diffuse = Vector3f(0.9f, 0.5f, 0.5f)
            /**
             * This attribute marks the box as touchable by [VRTouch].
             * If there is an intersection with a box and the left controller,
             * that box is slightly nudged in the direction
             * of the controller's velocity.*/
            obj.addAttribute(Touchable::class.java, Touchable(onTouch = { device ->
                if (leftControllerPushes) {
                    if (device.role == TrackerRole.LeftHand) {
                        obj.ifSpatial {
                            position = (device.velocity ?: Vector3f(0.0f)) * 0.05f + position
                        }
                    }
                }
            }))
            obj.addAttribute(Grabable::class.java, Grabable())
            obj.addAttribute(Selectable::class.java, Selectable(onSelect = { selectionStorage = obj }))
            obj
        }

        boxes.forEach { scene.addChild(it) }

        val pressableSphere = Sphere(0.1f)
        pressableSphere.spatial {
            position = Vector3f(0.5f, 1.0f, 0f)
        }
        pressableSphere.addAttribute(
            Pressable::class.java,
            SimplePressable(onRelease = { Wiggler(pressableSphere.spatial(), 0.1f, 2000) })
        )
        scene.addChild(pressableSphere)

        // remote controlled node
        val rcBox = Box(Vector3f(0.1f,0.05f,0.07f)).apply {
            spatial().position = Vector3f(0f, 1.0f, 1.0f)
            scene.addChild(this)

        }
        // remote control
        Sphere(0.05f).apply {
            spatial().position = Vector3f(0f, 1.0f, 0.5f)
            addAttribute(Grabable::class.java, Grabable(target = rcBox.spatial()))
            scene.addChild(this)

        }

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

        val pen = Box(Vector3f(0.05f, 0.13f, 0.05f))
        pen.spatial {
            position = Vector3f(-0.5f, 1.0f, 0f)
        }
        scene.addChild(pen)
        val tip = Box(Vector3f(0.025f, 0.025f, 0.025f))
        tip.spatial {
            position = Vector3f(0f, 0.08f, 0f)
        }
        pen.addChild(tip)
        var lastPenWriting = 0L
        pen.addAttribute(Grabable::class.java, Grabable())
        pen.addAttribute(
            Pressable::class.java, PerButtonPressable(
                mapOf(
                    OpenVRHMD.OpenVRButton.Trigger to SimplePressable(onHold = {
                        if (System.currentTimeMillis() - lastPenWriting > 50) {
                            val ink = Sphere(0.03f)
                            ink.spatial().position = tip.spatial().worldPosition()
                            scene.addChild(ink)
                            lastPenWriting = System.currentTimeMillis()
                        }
                    }),
                    OpenVRHMD.OpenVRButton.A to SimplePressable(onHold = {
                        if (System.currentTimeMillis() - lastPenWriting > 50) {
                            val ink = Box(Vector3f(0.03f))
                            ink.spatial().position = tip.spatial().worldPosition()
                            scene.addChild(ink)
                            lastPenWriting = System.currentTimeMillis()
                        }

                    })
                )
            )
        )


        thread {
            while (!running) {
                sleep(200)
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

        /** example of click input
        // Now we add another behaviour for toggling visibility of the boxes
        hmd.addBehaviour("toggle_boxes", ClickBehaviour { _, _ ->
        boxes.forEach { it.visible = !it.visible }
        logger.info("Boxes visible: ${boxes.first().visible}")
        })
        // ...and bind that to the A button of the left-hand controller.
        hmd.addKeyBinding("toggle_boxes", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.A)
         */

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

        VRSelect.createAndSet(
            scene,
            hmd,
            listOf(OpenVRHMD.OpenVRButton.Trigger),
            listOf(TrackerRole.LeftHand),
            { n ->
                // this is just some action to show a successful selection.
                // Party Cube!
                val w = Wiggler(n.spatialOrNull()!!, 1.0f)
                thread {
                    sleep(2 * 1000)
                    w.deativate()
                }
            },
            true
        )

        // a selection without a general action. Executes just the [onSelect] function of the [Selectable]
        VRSelect.createAndSet(
            scene,
            hmd,
            listOf(OpenVRHMD.OpenVRButton.Trigger),
            listOf(TrackerRole.RightHand)
        )

        VRScale.createAndSet(hmd, OpenVRHMD.OpenVRButton.Side) {
            selectionStorage?.ifSpatial { scale *= Vector3f(it) }
        }

        val menu = VRSelectionWheel.createAndSet(
            scene, hmd,
            listOf(OpenVRHMD.OpenVRButton.Menu), listOf(TrackerRole.LeftHand),
            listOf("Loading please wait" to {})
        )
        thread {
            sleep(5000) // or actually load something
            menu.get().actions = listOf(
                "Toggle Shell" to {
                    hullbox.visible = !hullbox.visible
                    logger.info("Hull visible: ${hullbox.visible}")
                },
                "Toggle Boxes" to {
                    boxes.forEach { it.visible = !it.visible }
                    logger.info("Boxes visible: ${boxes.first().visible}")
                },
                "Tree Selection Wheel" to {
                    val w = WheelMenu(hmd, listOf(
                        Action("Test sub wheel") { println("test fix sub wheel") }
                    ), true)
                    w.spatial().position = menu.get().controller.worldPosition(Vector3f())
                    scene.addChild(w)
                },
                "Toggle Push Left" to {
                    leftControllerPushes = !leftControllerPushes
                }).toActions()
        }

        VRTreeSelectionWheel.createAndSet(
            scene, hmd,
            listOf(OpenVRHMD.OpenVRButton.Menu), listOf(TrackerRole.RightHand),
            listOf(
                Switch("switch 1", false) { println("switch has been set to $it") },
                Action("dummy1", false) { println("A dummy entry has been pressed") },
                SubWheel(
                    "Menu1", listOf(
                        Action("dummy1-1") { println("A dummy entry has been pressed") },
                        Action("dummy1-2") { println("A dummy entry has been pressed") },
                        Action("dummy1-3") { println("A dummy entry has been pressed") }
                    )
                ),
                SubWheel(
                    "Menu2", listOf(
                        Action("dummy2-1") { println("A dummy entry has been pressed") },
                        SubWheel(
                            "Menu2-1", listOf(
                                Action("dummy2-1-1") { println("A dummy entry has been pressed") },
                                Action("dummy2-1-2") { println("A dummy entry has been pressed") },
                                Action("dummy2-1-3") { println("A dummy entry has been pressed") }
                            )
                        ),
                        Action("dummy2-3") { println("A dummy entry has been pressed") }
                    )
                ),
                Action("dummy2") { println("A dummy entry has been pressed") },
                Action("dummy3") { println("A dummy entry has been pressed") },
                Action("dummy4") { println("A dummy entry has been pressed") },
            )
        )
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VRControllerExample().main()
        }
    }
}
