package graphics.scenery.tests.examples.advanced

import bdv.util.AxisOrder
import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.behaviours.Grabable
import graphics.scenery.controls.behaviours.VRGrab
import graphics.scenery.volumes.SlicingPlane
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import ij.IJ
import ij.ImagePlus
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.scijava.ui.behaviour.ClickBehaviour
import tpietzsch.example2.VolumeViewerOptions
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Example for usage of VR controllers. Demonstrates the use of custom key bindings on the
 * HMD, and the use of intersection testing with scene elements.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VRVolumeCroppingExample : SceneryBase(VRVolumeCroppingExample::class.java.simpleName,
    windowWidth = 1920, windowHeight = 1200) {
    private lateinit var hmd: OpenVRHMD
    private lateinit var boxes: List<Node>
    private lateinit var hullbox: Box
    private lateinit var volume: Volume

    override fun init() {
        hmd = OpenVRHMD(useCompositor = true)

        if(!hmd.initializedAndWorking()) {
            logger.error("This demo is intended to show the use of OpenVR controllers, but no OpenVR-compatible HMD could be initialized.")
            exitProcess(1)
        }

        hub.add(SceneryElement.HMDInput, hmd)
        VRGrab.createAndSet(scene,hmd, listOf(OpenVRHMD.OpenVRButton.Side), listOf(TrackerRole.RightHand))

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        renderer?.toggleVR()

        val cam: Camera = DetachedHeadCamera(hmd)
        cam.spatial {
            position = Vector3f(0.0f, 0.0f, 0.0f)
        }

        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)

        scene.addChild(cam)

        val imp: ImagePlus = IJ.openImage("https://imagej.nih.gov/ij/images/t1-head.zip")
        val img: Img<UnsignedShortType> = ImageJFunctions.wrapShort(imp)

        volume = Volume.fromRAI(img, UnsignedShortType(), AxisOrder.DEFAULT, "T1 head", hub, VolumeViewerOptions())
        volume.transferFunction = TransferFunction.ramp(0.001f, 0.5f, 0.3f)
        volume.spatial {
            scale = Vector3f(0.5f,-0.5f,0.5f)
            position = Vector3f(0f,1f,-1f)
        }
        scene.addChild(volume)

        val croppingHandle = Box(Vector3f(0.2f,0.01f,0.2f))
        croppingHandle.spatial{
            position = Vector3f(0f,1f,-0.5f)
        }
        croppingHandle.addAttribute(Grabable::class.java, Grabable())
        scene.addChild(croppingHandle)

        val croppingPlane = SlicingPlane()
        croppingPlane.addTargetVolume(volume)
        volume.slicingMode = Volume.SlicingMode.Cropping
        croppingHandle.addChild(croppingPlane)

        (0..5).map {
            val light = PointLight(radius = 15.0f)
            light.emissionColor = Random.random3DVectorFromRange(0.0f, 1.0f)
            light.spatial {
                position = Random.random3DVectorFromRange(-5.0f, 5.0f)
            }
            light.intensity = 1.0f

            light
        }.forEach { scene.addChild(it) }

        hullbox = Box(Vector3f(20.0f, 20.0f, 20.0f), insideNormals = true)
        hullbox.material {
            ambient = Vector3f(0.6f, 0.6f, 0.6f)
            diffuse = Vector3f(0.4f, 0.4f, 0.4f)
            specular = Vector3f(0.0f, 0.0f, 0.0f)
            cullingMode = Material.CullingMode.Front
        }

        scene.addChild(hullbox)

        thread {
            while(!running) {
                Thread.sleep(200)
            }

            hmd.events.onDeviceConnect.add { hmd, device, timestamp ->
                if(device.type == TrackedDeviceType.Controller) {
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
        // Finally, add a behaviour to toggle the scene's shell
        hmd.addBehaviour("toggle_shell", ClickBehaviour { _, _ ->
            hullbox.visible = !hullbox.visible
            logger.info("Hull visible: ${hullbox.visible}")
        })
        //... and bind that to the A button on the left-hand controller.
        hmd.addKeyBinding("toggle_shell", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.A)

        // slicing mode toggle
        hmd.addBehaviour("toggleSlicing", ClickBehaviour{ _, _ ->
            val current = volume.slicingMode.id
            val next = (current + 1 ) % Volume.SlicingMode.values().size
            volume.slicingMode = Volume.SlicingMode.values()[next]
        })
        hmd.addKeyBinding("toggleSlicing",TrackerRole.RightHand,OpenVRHMD.OpenVRButton.A)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VRVolumeCroppingExample().main()
        }
    }
}
