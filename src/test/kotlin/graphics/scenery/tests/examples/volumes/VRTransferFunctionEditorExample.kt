package graphics.scenery.tests.examples.volumes

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.numerics.Random
import graphics.scenery.ui.SwingBridgeFrame
import graphics.scenery.ui.SwingUiNode
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunctionEditor
import graphics.scenery.volumes.Volume
import microscenery.VRUI.swingBridge.VRUICursor
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.ui.behaviour.DragBehaviour
import tpietzsch.example2.VolumeViewerOptions
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlin.system.exitProcess


/**
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 * Example for scenery - swing bridge
 *
 * A TransferFunctionEditor example to add, manipulate and remove control points of a volume's transfer function.
 * Further more able to generate a histogram representation of the volume data distribution to help with the transfer function setup.
 *
 * Usage with VR:
 */
class VRTransferFunctionEditorExample : SceneryBase("VRTransferFunctionEditor Example", 1280, 720, false) {
    var maxCacheSize = 512
    var cam: Camera = DetachedHeadCamera()

    private lateinit var hmd : OpenVRHMD

    /**
     * Sets up the example, containing 2 light sources (PointLight), a perspective camera and a volume.
     * Also adds a SwingUINode containing a SwingBridgeFrame contained by a TransferFunctionUI to manipulate the Volume
     */
    override fun init() {
        hmd = OpenVRHMD(useCompositor = true)

        if (!hmd.initializedAndWorking()) {
            logger.error("This demo is intended to show the use of OpenVR controllers, but no OpenVR-compatible HMD could be initialized.")
            exitProcess(1)
        }

        hub.add(SceneryElement.HMDInput, hmd)

        renderer = hub.add( SceneryElement.Renderer, Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        renderer?.toggleVR()


        cam = DetachedHeadCamera(hmd)
        cam.spatial().position = Vector3f(0.0f, 0.0f, 0.0f)
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        scene.addChild(cam)

        val lights = Light.createLightTetrahedron<PointLight>(spread = 5.0f, radius = 8.0f)
        lights.forEach {
            it.emissionColor = Random.random3DVectorFromRange(0.8f, 1.0f)
            scene.addChild(it)
        }

        val hullbox = Box(Vector3f(20.0f, 20.0f, 20.0f), insideNormals = true)
        hullbox.material {
            ambient = Vector3f(0.6f, 0.6f, 0.6f)
            diffuse = Vector3f(0.4f, 0.4f, 0.4f)
            specular = Vector3f(0.0f, 0.0f, 0.0f)
            cullingMode = Material.CullingMode.Front
        }
        scene.addChild(hullbox)

        val options = VolumeViewerOptions().maxCacheSizeInMB(maxCacheSize)
        //Currently only .xml volume formats are usable
        val v = Volume.fromXML("models/volumes/t1-head.xml", hub, options)
        v.name = "t1-head"
        v.colormap = Colormap.get("grays")
        v.spatial().position = Vector3f(-1.0f, 1.2f, -2.0f)
        v.spatial().scale = Vector3f(0.1f)
        v.setTransferFunctionRange(0.0f, 1000.0f)
        scene.addChild(v)

        val bridge = SwingBridgeFrame("1DTransferFunctionEditor")
        val tfUI = TransferFunctionEditor(650, 550, v, bridge)
        val swingUiNode = tfUI.mainFrame.uiNode
        swingUiNode.spatial() {
            position = Vector3f(1.0f,1.2f,-2.0f)
        }
        scene.addChild(swingUiNode)



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

    /**
     * Adds InputBehaviour -> MouseClick, Drag and Ctrl-Click to interact with the SwingUI using a Scenery Plane (SwingUINode)
     */
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

        VRUICursor.createAndSet(scene, hmd, listOf( OpenVRHMD.OpenVRButton.Trigger),
            listOf(TrackerRole.RightHand))

        hmd.events.onDeviceConnect.add { _, device, _ ->
            if (device.type == TrackedDeviceType.Controller) {
                device.model?.let { controller ->
                    hmd.addBehaviour("aa", ClickBehaviour { x, y -> logger.warn("lol") })
                    hmd.addKeyBinding("aa", device.role, OpenVRHMD.OpenVRButton.Menu)

                    }
                }
            }
    }

    /**
     * Static object for running as application
     */
    companion object {
        /**
         * Main method for the application, that instances and runs the example.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            VRTransferFunctionEditorExample().main()
        }
    }
}


