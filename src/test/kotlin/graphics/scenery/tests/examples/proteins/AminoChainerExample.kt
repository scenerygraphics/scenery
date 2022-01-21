package graphics.scenery.tests.examples.proteins

import org.joml.*
import graphics.scenery.*
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.*
import graphics.scenery.flythroughs.IUPACAbbreviationsReader
import graphics.scenery.proteins.chemistry.AminoChainer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 *
 *
 * @author Justin Buerger <burger@mpi-cbg.de>
 */
class AminoChainerExample: SceneryBase("RainbowRibbon", windowWidth = 1280, windowHeight = 720) {
    private lateinit var hmd: OpenVRHMD
    private lateinit var hullbox: Box
    private val lightCount = 20
    private val positionRange = 250f

        override fun init() {

            hmd = OpenVRHMD(seated = true, useCompositor = true)

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

            val lights = (0 until lightCount).map { PointLight(radius = positionRange) }

            lights.map {
                it.spatial {
                    position = Random.random3DVectorFromRange(-positionRange/2, positionRange/2)
                }
                it.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
                it.intensity = 0.5f

                scene.addChild(it)
            }

            hullbox = Box(Vector3f(40.0f, 40.0f, 40.0f), insideNormals = true)
            hullbox.material {
                ambient = Vector3f(0.6f, 0.6f, 0.6f)
                diffuse = Vector3f(0.4f, 0.4f, 0.4f)
                specular = Vector3f(0.0f, 0.0f, 0.0f)
                cullingMode = Material.CullingMode.Front
            }

            scene.addChild(hullbox)

            val aaOverview = Box(Vector3f(1.0f, 1.0f, 1.0f))
            aaOverview.name = "aaOverview"
            val img = Image.fromResource("aa-overview.png", ProteinBuilderExample::class.java)
            val height = img.height
            val width = img.width
            aaOverview.material {
                textures["diffuse"] = Texture.fromImage(img)
            }
            aaOverview.spatial().scale = Vector3f(3*width/height.toFloat()/5f, 3/5f, 0f)
            aaOverview.visible = false
            scene.addChild(aaOverview)
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
                            if (device.role == TrackerRole.RightHand && controller.children.first().spatialOrNull() != null) {
                                aaOverview.update.add { aaOverview.spatial().position = controller.children.first().spatialOrNull()!!.worldPosition()
                                    aaOverview.spatial().rotation = Quaternionf(hmd.getOrientation()).conjugate().normalize()}
                            }
                        }
                    }
                }
            }

            thread {
                Thread.sleep(10000)
                cam.showMessage("Hello friend!", duration= 6000)
                Thread.sleep(6000)
                cam.showMessage("Welcome to the protein forge!", duration = 6000)
                Thread.sleep(6000)
                cam.showMessage("There is an amino acid on your left hand", duration = 8000)
                Thread.sleep(6000)
                cam.showMessage("And in front of you!", duration = 8000)
                Thread.sleep(6000)
                cam.showMessage("Choose the right acid", duration = 8000)
                Thread.sleep(6000)
                cam.showMessage("And build your polypeptide!", duration = 8000)
                Thread.sleep(6000)
                cam.showMessage("Use A to select the amino acid.", duration = 8000)
                Thread.sleep(8000)
                cam.showMessage("Use right side button to see all amino acids.", duration = 8000)
                Thread.sleep(8000)
                cam.showMessage("Use X to just advance", duration = 8000)
                Thread.sleep(8000)
                cam.showMessage("Click left side to disable animation", duration = 8000)
                Thread.sleep(8000)
                cam.showMessage("Click left side again to enable it", duration = 8000)
                Thread.sleep(8000)
                cam.showMessage("Have fun!", duration = 8000)
                Thread.sleep(8000)
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
        VRPress.createAndSet(scene, hmd, listOf(OpenVRHMD.OpenVRButton.Trigger), listOf(TrackerRole.LeftHand,TrackerRole.RightHand))
        VRTouch.createAndSet(scene,hmd, listOf(TrackerRole.RightHand,TrackerRole.LeftHand),true)
        AminoChainer.createAndSet("3nir", scene, hmd, listOf(OpenVRHMD.OpenVRButton.A), listOf(TrackerRole.LeftHand))

        val iupacAbbreviations = IUPACAbbreviationsReader().abbrevations

        var numberOfTries = 0
        fun rightProteinChosen(threeLetterCode: String) {
            if (scene.children.filter { it.name == "greetings" }.isNotEmpty()) {
                scene.removeChild("greetings")
            }
            if(hmd.getBehaviour("aaForge") is AminoChainer) {
                val builder = hmd.getBehaviour("aaForge") as AminoChainer
                val currentCode = builder.currentCode
                numberOfTries = if (currentCode == threeLetterCode) {
                    //x and y are never used in this implementation of click(), hence, 1 is chosen as an arbitrary value
                    builder.click(1, 1)
                    0
                } else {
                    when(numberOfTries) {
                        0-> { scene.activeObserver?.showMessage( "Friend, try ${iupacAbbreviations[currentCode]?.chemicalCategory}", duration = 5000) }
                        1 -> { scene.activeObserver?.showMessage( "Friend, try ${iupacAbbreviations[currentCode]?.chemicalCategory}", duration = 5000) }
                        2 -> { scene.activeObserver?.showMessage( "Friend, try ${iupacAbbreviations[currentCode]?.fullName}", duration = 5000) }
                        else -> {logger.warn("NumberOfTries became $numberOfTries this should not happen.")}
                    }
                    (numberOfTries+1)%3
                }
            }
        }

        VRTreeSelectionWheel.createAndSet(scene, hmd,
            listOf(OpenVRHMD.OpenVRButton.A), listOf(TrackerRole.RightHand),
            listOf(
                SubWheel("Acid", listOf(
                    Action("Aspartic Acid") { rightProteinChosen("ASP") },
                    Action("Glutamic Acid") { rightProteinChosen("GLU") }
                )),
                SubWheel("Basic", listOf(
                    Action("Arginine") { rightProteinChosen("ARG") },
                    Action("Histidine") { rightProteinChosen("HIS") },
                    Action("Lysine") { rightProteinChosen("LYS") }
                )),
                SubWheel("Hydrophobic", listOf(
                    Action("Alanine") { rightProteinChosen("ALA") },
                    Action("Isoleucine") { rightProteinChosen("ILE") },
                    Action("Leucine") { rightProteinChosen("LEU") },
                    Action("Methionine") { rightProteinChosen("MET") },
                    Action("Phenylalanine") { rightProteinChosen("PHE") },
                    Action("Proline") { rightProteinChosen("PRO") },
                    Action("Tryptophane") { rightProteinChosen("TRP") },
                    Action("Valine") { rightProteinChosen("VAL") }
                )),
                SubWheel("Polar", listOf(
                    Action("Asparagine") { rightProteinChosen("ASN") },
                    Action("Cysteine") { rightProteinChosen("CYS") },
                    Action("Glutamine") { rightProteinChosen("GLN") },
                    Action("Glycin") { rightProteinChosen("GLY") },
                    Action("Serine") { rightProteinChosen("SER") },
                    Action("Threonine") { rightProteinChosen("THR") },
                    Action("Tyrosine") { rightProteinChosen("TYR") }
                ))
            ))

        fun animation() {
            val builder = hmd.getBehaviour("aaForge") as AminoChainer
            builder.showAnimation = !builder.showAnimation
            val message = if(builder.showAnimation) { "Animation eneabled!" } else { "Animation disabled!" }
            scene.activeObserver?.showMessage( message, duration = 5000)
        }
        //show amino acid overview
        hmd.addBehaviour("amino_overview", ClickBehaviour { _, _ ->
            scene.children.first { it.name == "aaOverview" }.visible = !scene.children.first { it.name == "aaOverview" }.visible
        })
        hmd.addKeyBinding("amino_overview", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Side)
        //show animation or show no animation
        hmd.addBehaviour("animation", ClickBehaviour { _, _ ->
           animation()
        })
        hmd.addKeyBinding("animation", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Side)
        setupCameraModeSwitching()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            AminoChainerExample().main()
        }
    }
}



