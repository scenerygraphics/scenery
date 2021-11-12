package graphics.scenery.tests.examples.proteins


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
 */
class ProteinBuilderExample : SceneryBase(
    ProteinBuilderExample::class.java.simpleName,
    windowWidth = 1920, windowHeight = 1200
) {
    private lateinit var hmd: OpenVRHMD
    private lateinit var hullbox: Box
    private var leftControllerPushes = true

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

        VRTreeSelectionWheel.createAndSet(scene, hmd,
            listOf(OpenVRHMD.OpenVRButton.A), listOf(TrackerRole.RightHand),
            listOf(
                Action("Acid") { println("Acid has been chosen") },
                SubWheel("Acid", listOf(
                    Action("Aspartic Acid",{println("A dummy entry has been pressed")}),
                    Action("Glutamic Acid",{println("A dummy entry has been pressed")})
                )),
                Action("Basic") { println("Basic has been chosen") },
                SubWheel("Basic", listOf(
                    Action("Arginine",{println("A dummy entry has been pressed")}),
                    Action("Histidine",{println("A dummy entry has been pressed")}),
                    Action("Lysine",{println("A dummy entry has been pressed")})
                )),
                Action("Hydrophobic") { println("Hydrophobic has been chosen") },
                SubWheel("Hydrophobic", listOf(
                    Action("Alanine",{println("A dummy entry has been pressed")}),
                    Action("Isoleucine",{println("A dummy entry has been pressed")}),
                    Action("Leucine",{println("A dummy entry has been pressed")}),
                    Action("Methionine",{println("A dummy entry has been pressed")}),
                    Action("Phenylalanine",{println("A dummy entry has been pressed")}),
                    Action("Proline",{println("A dummy entry has been pressed")}),
                    Action("Tryptophane",{println("A dummy entry has been pressed")}),
                    Action("Valine",{println("A dummy entry has been pressed")})

                )),
                Action("Polar") { println("Polar has been chosen") },
                SubWheel("Polar", listOf(
                    Action("Asparagine",{println("A dummy entry has been pressed")}),
                    Action("Cysteine",{println("A dummy entry has been pressed")}),
                    Action("Glutamine",{println("A dummy entry has been pressed")}),
                    Action("Glycin",{println("A dummy entry has been pressed")}),
                    Action("Serine",{println("A dummy entry has been pressed")}),
                    Action("Threonine",{println("A dummy entry has been pressed")}),
                    Action("Thyrosine",{println("A dummy entry has been pressed")})
                ))
            ))
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ProteinBuilderExample().main()
        }
    }
}

