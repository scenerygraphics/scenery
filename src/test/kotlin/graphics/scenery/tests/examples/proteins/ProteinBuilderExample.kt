package graphics.scenery.tests.examples.proteins


import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.*
import graphics.scenery.flythroughs.IUPACAbbreviationsReader
import graphics.scenery.flythroughs.ProteinBuilder
import graphics.scenery.numerics.Random
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.RibbonDiagram
import graphics.scenery.utils.Wiggler
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
    private val ribbon = RibbonDiagram(Protein.fromID("3nir"))
    var firstClick = true

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

        //initialize ribbondiagram
        ribbon.name = "3nir"
        ribbon.visible = false
        scene.addChild(ribbon)
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

        VRTouch.createAndSet(scene,hmd, listOf(TrackerRole.RightHand,TrackerRole.LeftHand),true)

        VRGrab.createAndSet(scene, hmd, listOf(OpenVRHMD.OpenVRButton.Side), listOf(TrackerRole.LeftHand,TrackerRole.RightHand))
        VRPress.createAndSet(scene, hmd, listOf(OpenVRHMD.OpenVRButton.Trigger), listOf(TrackerRole.LeftHand,TrackerRole.RightHand))

        val selectionStorage =
            VRSelect.createAndSetWithStorage(
                scene,
                hmd,
                listOf(OpenVRHMD.OpenVRButton.Trigger),
                listOf(TrackerRole.RightHand)
            )
        VRScale.createAndSet(hmd, OpenVRHMD.OpenVRButton.Side) {
            selectionStorage.selected?.ifSpatial { scale *= Vector3f(it) }
        }

        VRSelect.createAndSetWithAction(scene,
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
            })

        ProteinBuilder.createAndSet(ribbon, scene, "3nir", hmd, listOf(OpenVRHMD.OpenVRButton.A), listOf(TrackerRole.LeftHand))

        val iupacAbbreviations = IUPACAbbreviationsReader().abbrevations

        var numberOfTries = 0
        fun rightProteinChosen(threeLetterCode: String) {
            if(hmd.getBehaviour("ProteinBuilder") is ProteinBuilder) {
                val builder = hmd.getBehaviour("ProteinBuilder") as ProteinBuilder
                val currentCode = builder.currentAminoCode
                if ((currentCode != null && currentCode == threeLetterCode) || firstClick) {
                    //x and y are never used in this implementation of click(), hence, 1 is chosen as an arbitrary value
                    builder.click(1, 1)
                    firstClick = false
                    numberOfTries = 0
                } else {
                    when(numberOfTries) {
                        0-> { print("Try ${iupacAbbreviations[currentCode]?.chemicalCategory}") }
                        1 -> { print("Try ${iupacAbbreviations[currentCode]?.chemicalCategory}") }
                        2 -> { print("Try ${iupacAbbreviations[currentCode]?.fullName}") }
                        else -> {logger.warn("NumberOfTries became $numberOfTries this should not happen.")}
                    }
                    numberOfTries = (numberOfTries+1)%3
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


    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ProteinBuilderExample().main()
        }
    }
}

