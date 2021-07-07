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
import kotlin.math.min
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
    private lateinit var sideChains: AminoAcidsStickAndBall
    private val chosenCurveSection = mutableListOf<Int>()

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
        sideChains = AminoAcidsStickAndBall(protein)
        scene.addChild(sideChains)
        ribbon = RibbonDiagram(protein)
        scene.addChild(ribbon)

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
                            chosenCurveSection.clear()
                            ribbon.children.forEach{
                                rainbow.colorVector(it)
                            }
                            ribbon.children.flatMap { subProtein -> subProtein.children }.forEachIndexed { index, subCurve ->
                                if(controller.children.first().intersects(subCurve)) {
                                    subCurve.material.diffuse = Vector3f(1f, 0f, 0f)
                                    chosenCurveSection.add(index)
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
        //another behaviour to add the sidechains to the scene
        hmd.addBehaviour("show_side_chain", ClickBehaviour { _, _ ->
            chosenCurveSection.forEach { curveSectionIndex ->
                sideChains.children[curveSectionIndex].visible = !sideChains.children[curveSectionIndex].visible
                logger.info("$curveSectionIndex side chain should be: ${sideChains.children[curveSectionIndex].visible}")
                /*
                val curveSection = ribbonSections[curveSectionIndex]
                val curveSectionPosition = curveSection.position
                val residues = ribbon.groups.filter { it.hasAminoAtoms() }
                var rightIndex = curveSectionIndex
                if (ribbonSections.size == residues.size) {
                    clickedSideChains[curveSectionIndex] = !clickedSideChains[curveSectionIndex]
                    residues[curveSectionIndex] }
                /*
                        very rarely there is a slight difference of one between the number of sections and residues
                        in this case we choose the residue which is closest to our section
                     */
                else {
                    when {
                        (curveSectionIndex == 0) -> {
                            clickedSideChains[curveSectionIndex] = !clickedSideChains[curveSectionIndex]
                            residues.first()
                        }
                        (curveSectionIndex >= residues.lastIndex) -> {
                            clickedSideChains[curveSectionIndex] = !clickedSideChains[curveSectionIndex]
                            residues.last()
                        }
                        else -> {
                            val ca1 = residues[curveSectionIndex - 1].getAtom("CA")
                            val ca1Vec = Vector3f(ca1.x.toFloat(), ca1.y.toFloat(), ca1.z.toFloat())
                            val ca2 = residues[curveSectionIndex].getAtom("CA")
                            val ca2Vec = Vector3f(ca2.x.toFloat(), ca2.y.toFloat(), ca2.z.toFloat())
                            val ca3 = residues[curveSectionIndex].getAtom("CA")
                            val ca3Vec = Vector3f(ca3.x.toFloat(), ca3.y.toFloat(), ca3.z.toFloat())
                            if (ca1Vec.sub(curveSectionPosition, ca1Vec).length() <= ca2Vec.sub(curveSectionPosition, ca2Vec).length()
                                && ca1Vec.sub(curveSectionPosition, ca1Vec).length() <= ca3Vec.sub(curveSectionPosition, ca3Vec).length()) {
                                    rightIndex --
                                    clickedSideChains[curveSectionIndex-1] = !clickedSideChains[curveSectionIndex-1]
                                    residues[curveSectionIndex - 1]
                            } else if (ca2Vec.sub(curveSectionPosition, ca2Vec).length() <= ca3Vec.sub(curveSectionPosition, ca3Vec).length()) {
                                clickedSideChains[curveSectionIndex] = !clickedSideChains[curveSectionIndex]
                                residues[curveSectionIndex]
                            } else {
                                rightIndex++
                                clickedSideChains[curveSectionIndex+1] = !clickedSideChains[curveSectionIndex+1]
                                residues[curveSectionIndex + 1]
                            }
                        }
                    }
                }
                */
            }
        })
        // ...and bind that to the side button of the right-hand controller.
        hmd.addKeyBinding("show_side_chain", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Trigger)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VRSidecChainsExample().main()
        }
    }
}
