package graphics.scenery.tests.examples.cluster

import cleargl.GLVector
import coremem.enums.NativeTypeEnum
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.controls.behaviours.GamepadCameraControl
import graphics.scenery.controls.behaviours.GamepadClickBehaviour
import graphics.scenery.controls.behaviours.GamepadMovementControl
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.java.games.input.Component
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Demo reel example to be run on a CAVE system.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ProteinsComparisonExample: SceneryBase("ProteinComparison") {
    var hmd: TrackedStereoGlasses? = null
    var publishedNodes = ArrayList<Node>()

    var cam = DetachedHeadCamera()

    override fun init() {
        logger.warn("*** WARNING - EXPERIMENTAL ***")
        logger.warn("This is an experimental example, which might need additional configuration on your computer")
        logger.warn("or might not work at all. You have been warned!")

        hmd = TrackedStereoGlasses("DTrack@10.1.2.201", screenConfig = "CAVEExample.yml")
        hub.add(SceneryElement.HMDInput, hmd!!)

        renderer = Renderer.createRenderer(hub, applicationName, scene, 2560, 1600)
        hub.add(SceneryElement.Renderer, renderer!!)
        settings.set("Renderer.HDR.Exposure", 5.0f)

        cam = DetachedHeadCamera(hmd)
        with(cam) {
            //            position = GLVector(0.0f, -1.3190879f, 0.8841703f)
            position = GLVector(0.0f, 0.0f, 2.0f)
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight, 0.02f, 500.0f)
            active = true
            disableCulling = true

            scene.addChild(this)
        }

        // box setup

        val shell = Box(GLVector(120.0f, 120.0f, 120.0f), insideNormals = true)
        shell.material.cullingMode = Material.CullingMode.Front
        shell.material.diffuse = GLVector(0.0f, 0.0f, 0.0f)
        shell.material.specular = GLVector.getNullVector(3)
        shell.material.ambient = GLVector.getNullVector(3)
        //scene.addChild(shell)

        // lighting setup

        val lights = (0..4).map {
            PointLight(150.0f)
        }

        val tetrahedron = listOf(
            GLVector(1.0f, 0f, -1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(-1.0f,0f,-1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(0.0f,1.0f,1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(0.0f,-1.0f,1.0f/Math.sqrt(2.0).toFloat()))

        tetrahedron.mapIndexed { i, position ->
            lights[i].position = position * 10.0f
            lights[i].emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            lights[i].intensity = 10.0f
            scene.addChild(lights[i])
        }

        // scene setup
        val driveLetter = System.getProperty("scenery.DriveLetter", "E")

        val protein1 = Mesh("2zzm")
        protein1.readFrom("$driveLetter:/protein-models/2zzm.obj")
        protein1.fitInto(2.0f)
        protein1.position = GLVector(2.0f, 0.0f, 0.0f)
        scene.addChild(protein1)

        val grid1 = BoundingGrid()
        grid1.node = protein1
        grid1.gridColor = GLVector(1.0f, 0.0f, 0.0f)

        val protein2 = Mesh("4yvj")
        protein2.readFrom("$driveLetter:/protein-models/4yvj_2.obj")
        protein2.fitInto(2.0f)
        protein2.position = GLVector(-2.0f, 0.0f, 1.5f)
        scene.addChild(protein2)

        val grid2 = BoundingGrid()
        grid2.node = protein2

        publishedNodes.add(cam)
        publishedNodes.add(protein1)
        publishedNodes.add(protein2)
        publishedNodes.add(grid1)
        publishedNodes.add(grid2)
        //lights.forEach { publishedNodes.add(it) }

        val publisher = hub.get<NodePublisher>(SceneryElement.NodePublisher)
        val subscriber = hub.get<NodeSubscriber>(SceneryElement.NodeSubscriber)

        publishedNodes.forEachIndexed { index, node ->
            publisher?.nodes?.put(13337 + index, node)

            subscriber?.nodes?.put(13337 + index, node)
        }

        activeProtein = protein1
    }

    /**
     * Shows this [Node] and all children.
     */
    fun Node.showAll() {
        this.children.map { visible = true }
        this.visible = true
    }

    /**
     * Hides this [Node] and all children.
     */
    fun Node.hideAll() {
        this.children.map { visible = false }
        this.visible = false
    }

    /**
     * Standard input setup, plus additional key bindings to
     * switch scenes.
     */
    override fun inputSetup() {
        val inputHandler = (hub.get(SceneryElement.Input) as? InputHandler) ?: return

        val rotateLeft = ClickBehaviour { _, _ ->
//            activeScene.rotation = activeScene.rotation.rotateByAngleY(0.05f)
        }

        val rotateRight = ClickBehaviour { _, _ ->
//            activeScene.rotation = activeScene.rotation.rotateByAngleY(-0.05f)
        }

        val rotateUp = ClickBehaviour { _, _ ->
//            activeScene.rotation = activeScene.rotation.rotateByAngleX(-0.05f)
        }

        val rotateDown = ClickBehaviour { _, _ ->
//            activeScene.rotation = activeScene.rotation.rotateByAngleX(0.05f)
        }


        inputHandler.addBehaviour("rotate_left", rotateLeft)
        inputHandler.addBehaviour("rotate_right", rotateRight)
        inputHandler.addBehaviour("rotate_up", rotateUp)
        inputHandler.addBehaviour("rotate_down", rotateDown)

        inputHandler.removeBehaviour("gamepad_camera_control")
        inputHandler.addBehaviour("protein_movement",
            GamepadCameraControl("protein_movement", listOf(Component.Identifier.Axis.RX, Component.Identifier.Axis.RY), { activeProtein }))

        inputHandler.addKeyBinding("rotate_left", "J")
        inputHandler.addKeyBinding("rotate_right", "L")
        inputHandler.addKeyBinding("rotate_up", "I")
        inputHandler.addKeyBinding("rotate_down", "K")

        val toggleProteins = object : GamepadClickBehaviour {
            override fun click(p0: Int, p1: Int) {
                activeProtein.children.forEach {
                    if(it is BoundingGrid) {
                        it.gridColor = GLVector(0.0f, 0.0f, 0.0f)
                    }
                }

                activeProtein = if(activeProtein.name == "2zzm") {
                    scene.find("4yvj") as Mesh
                } else {
                    scene.find("2zzm") as Mesh
                }

                activeProtein.children.forEach {
                    if(it is BoundingGrid) {
                        it.gridColor = GLVector(1.0f, 0.0f, 0.0f)
                    }
                }

            }
        }

        inputHandler.addBehaviour("toggle_proteins", toggleProteins)
        inputHandler.addKeyBinding( "toggle_proteins", "1")
    }

    lateinit var activeProtein: Mesh

    @Test override fun main() {
        super.main()
    }
}
