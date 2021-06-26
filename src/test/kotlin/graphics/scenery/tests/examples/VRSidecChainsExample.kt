package graphics.scenery.tests.examples

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.numerics.Random
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Example for usage of VR controllers. Demonstrates the use of custom key bindings on the
 * HMD, and the use of intersection testing with scene elements.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VRSidecChainsExample : SceneryBase(VRSidecChainsExample::class.java.simpleName,
    windowWidth = 1920, windowHeight = 1200) {
    private lateinit var hmd: OpenVRHMD
    private lateinit var ribbon: RibbonDiagram
    private lateinit var hullbox: Box
    private var clickedSideChains = mutableListOf<Boolean>()
    private val chosenSideChains = mutableListOf<Int>()

    override fun init() {
        hmd = OpenVRHMD(useCompositor = true)

        if(!hmd.initializedAndWorking()) {
            logger.error("This demo is intended to show the use of OpenVR controllers, but no OpenVR-compatible HMD could be initialized.")
            exitProcess(1)
        }

        hub.add(SceneryElement.HMDInput, hmd)

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        renderer?.toggleVR()

        val cam: Camera = DetachedHeadCamera(hmd)
        cam.position = Vector3f(0.0f, 0.0f, 0.0f)

        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)

        scene.addChild(cam)

        val protein = Protein.fromID("3nir")
        ribbon = RibbonDiagram(protein)
        print(ribbon.protein.structure.chains.flatMap { it.atomGroups }.filter { it.hasAminoAtoms() }.size)
        scene.addChild(ribbon)
        val ribbonMap =  ribbon.children.flatMap { subProtein -> subProtein.children }.flatMap { curve -> curve.children }.flatMap { subcurve -> subcurve.children }
        clickedSideChains = ArrayList(ribbonMap.size)
        ribbonMap.forEachIndexed { index, _ ->
            clickedSideChains.add(false)
        }

        val lights = Light.createLightTetrahedron<PointLight>(spread = 5.0f, radius = 8.0f)
        lights.forEach {
            it.emissionColor = Random.random3DVectorFromRange(0.0f, 1.0f)
            scene.addChild(it)
        }

        hullbox = Box(Vector3f(20.0f, 20.0f, 20.0f), insideNormals = true)
        val hullboxMaterial = Material()
        hullboxMaterial.ambient = Vector3f(0.6f, 0.6f, 0.6f)
        hullboxMaterial.diffuse = Vector3f(0.4f, 0.4f, 0.4f)
        hullboxMaterial.specular = Vector3f(0.0f, 0.0f, 0.0f)
        hullboxMaterial.cullingMode = Material.CullingMode.Front
        hullbox.material = hullboxMaterial

        scene.addChild(hullbox)

        val rainbow = Rainbow()
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

                        // This update routine is called every time the position of the controller
                        // updates, and checks for intersections with any of the boxes. If there is
                        // an intersection with a box, that box is slightly nudged in the direction
                        // of the controller's velocity.
                        controller.update.add {
                            chosenSideChains.clear()
                            ribbon.children.forEach{
                                rainbow.colorVector(it)
                            }
                            ribbon.children.flatMap { subProtein -> subProtein.children }.flatMap { curve -> curve.children }.flatMap { subcurve -> subcurve.children }
                                .forEachIndexed { index, subCurve ->
                                    if(controller.children.first().intersects(subCurve)) {
                                        subCurve.material.diffuse = Vector3f(1f, 0f, 0f)
                                        chosenSideChains.add(index)
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
        // Now we add another behaviour for toggling visibility of the boxes
        hmd.addBehaviour("show_side_chain", ClickBehaviour { _, _ ->
            chosenSideChains.forEach { sideChainIndex ->
                print("I have been chosen! ${sideChainIndex}")
                clickedSideChains[sideChainIndex] = !clickedSideChains[sideChainIndex]}
            logger.info("residue chosen: ")
        })
        // ...and bind that to the side button of the left-hand controller.
        hmd.addKeyBinding("show_side_chain", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Trigger)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VRSidecChainsExample().main()
        }
    }
}
