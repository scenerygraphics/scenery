package graphics.scenery.tests.examples.cluster

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.GamepadButton
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.controls.behaviours.GamepadRotationControl
import graphics.scenery.controls.behaviours.GamepadClickBehaviour
import graphics.scenery.controls.behaviours.GamepadMovementControl
import graphics.scenery.controls.behaviours.withCooldown
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.RibbonDiagram
import net.java.games.input.Component
import org.joml.Vector3f
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

/**
 * Example for visually comparing two proteins. A gamepad can be used for navigation,
 * protein selection, and rotation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ProteinComparisonExample: SceneryBase("Protein Comparison Example") {
    var hmd: TrackedStereoGlasses? = null

    lateinit var activeProtein: Mesh
    var cam = DetachedHeadCamera()

    override fun init() {
        hmd = TrackedStereoGlasses("DTrack:body-1@224.0.1.1", screenConfig = "CAVEExample.yml")
        hub.add(SceneryElement.HMDInput, hmd!!)

        renderer = Renderer.createRenderer(hub, applicationName, scene, 1280, 720)
        hub.add(SceneryElement.Renderer, renderer!!)

        cam = DetachedHeadCamera(hmd)
        with(cam) {
            spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
            perspectiveCamera(50.0f, windowWidth, windowHeight, 0.02f, 500.0f)

            scene.addChild(this)
        }

        // box setup
//        val shell = Box.hulledBox(Vector3f(10.0f))
//        shell.material().diffuse = Vector3f(1.0f)
//        scene.addChild(shell)

        // lighting setup
        val lights = Light.createLightTetrahedron<PointLight>(spread = 5.0f, radius = 10.0f)
        lights.forEach { scene.addChild(it) }

        val protein1 = RibbonDiagram(Protein.fromID("2zzm"))
        protein1.name = protein1.protein.structure.name.lowercase()
        protein1.spatial {
            scale = Vector3f(0.04f)
            position = Vector3f(2.0f, 0.0f, 0.0f)
        }
        scene.addChild(protein1)

//        val grid1 = BoundingGrid()
//        grid1.node = protein1
//        grid1.gridColor = Vector3f(1.0f, 0.0f, 0.0f)

        val protein2 = RibbonDiagram(Protein.fromID("4yvj"))
        protein2.name = protein2.protein.structure.name.lowercase()
        protein2.spatial {
            scale = Vector3f(0.04f)
            position = Vector3f(-2.0f, 0.0f, 1.5f)
        }
        scene.addChild(protein2)

//        val grid2 = BoundingGrid()
//        grid2.node = protein2

        activeProtein = protein1
    }


    /**
     * Standard input setup, plus additional key bindings to
     * switch scenes.
     */
    @OptIn(ExperimentalTime::class)
    override fun inputSetup() {
        val inputHandler = (hub.get(SceneryElement.Input) as? InputHandler) ?: return

        val toggleProteins = object : GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                withCooldown(200.milliseconds) {
                    // finds the currently active protein, un-highlights it
                    activeProtein.children.forEach {
                        if (it is BoundingGrid) {
                            it.gridColor = Vector3f(0.0f, 0.0f, 0.0f)
                        }
                    }

                    // selects the new active protein
                    activeProtein = if (activeProtein.name == "2zzm") {
                        scene.find("4yvj") as Mesh
                    } else {
                        scene.find("2zzm") as Mesh
                    }

                    // highlights the newly active protein
                    activeProtein.children.forEach {
                        if (it is BoundingGrid) {
                            it.gridColor = Vector3f(1.0f, 0.0f, 0.0f)
                        }
                    }
                }
            }
        }

        // toggles the active protein by pressing 1 on the keyboard, or the B key
        // on the gamepad.
        inputHandler += (toggleProteins
            called "toggle_proteins"
            boundTo GamepadButton.Button1)

        // removes the default second-stick camera look-around for the gamepad
        inputHandler -= "gamepad_camera_control"
        // adds a new behaviour for rotating the [activeProtein], RX and RY are the rotation
        // axis on the Xbox Wireless controller. For other controllers, different axis may
        // have to be used. Gamepad movement and rotation behaviours are always active,
        // hence the [GamepadButton.AlwaysActive] key binding.
        inputHandler += (GamepadRotationControl(listOf(Component.Identifier.Axis.RY, Component.Identifier.Axis.RX), 1.0f) { activeProtein }
            called "protein_rotation"
            boundTo GamepadButton.AlwaysActive)

        inputHandler += (GamepadMovementControl(listOf(Component.Identifier.Axis.Z)) { cam }
            called "vertical_movement"
            boundTo GamepadButton.AlwaysActive)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ProteinComparisonExample().main()
        }
    }
}
