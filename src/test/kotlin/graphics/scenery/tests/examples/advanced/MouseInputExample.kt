package graphics.scenery.tests.examples.advanced

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.behaviours.MouseDragPlane
import graphics.scenery.controls.behaviours.MouseDragSphere
import graphics.scenery.controls.behaviours.MouseRotate
import graphics.scenery.controls.behaviours.SelectCommand
import graphics.scenery.effectors.LineRestrictionEffector
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import graphics.scenery.utils.extensions.plus
import org.joml.Vector3f
import kotlin.concurrent.thread

/**
 * Test for [SelectCommand], produces a lot of (clickable) spheres, that
 * wiggle when selected by double-click.
 *
 * Mouse button bindings:
 * - Left button drag: Rotate camera (default arcball control)
 * - Left double-click + drag on object: Rotate object
 * - Right button drag: Drag objects along a sphere around the camera
 * - Middle button drag: Drag objects along a plane parallel to the view plane
 * - Right double-click on object: Make object wiggle
 *
 * Keyboard + mouse bindings (for testing without mouse buttons):
 * - Hold "1" + drag: Same as left button drag (rotate camera)
 * - Hold "2" + drag: Same as middle button drag (drag objects along plane)
 * - Hold "3" + drag: Same as right button drag (drag objects along sphere)
 * - Hold "4" + drag: Same as left double-click + drag (rotate object)
 * - Hold "5" + click on object: Same as right double-click (make object wiggle)
 *
 * The green sphere is an example of how to restrict dragging.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Jan Tiemann
 */
class MouseInputExample : SceneryBase("MouseInputExample", 1280, 720) {
    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        for (i in 0 until 200) {
            if (i % 2 == 0) {
                val s = Icosphere(Random.randomFromRange(0.04f, 0.2f), 2)
                s.spatial().position = Random.random3DVectorFromRange(-5.0f, 5.0f)
                scene.addChild(s)
            } else {
                val box = Box(Random.random3DVectorFromRange(0.04f, 0.2f), insideNormals = true)
                box.material().diffuse = Vector3f(0f, 1.0f, 1.0f)
                box.spatial().position = Random.random3DVectorFromRange(-5.0f, 5.0f)
                scene.addChild(box)
            }
        }

        val box = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        box.material {
            diffuse = Vector3f(1.0f, 1.0f, 1.0f)
            cullingMode = Material.CullingMode.Front
        }
        scene.addChild(box)

        val largePlate = Box(Vector3f(7.0f, 1.0f, 8.0f))
        largePlate.material().diffuse = Vector3f(0.0f, 1.0f, 0.5f)
        largePlate.spatial().position = Vector3f(0f,-4.0f,0f)
        scene.addChild(largePlate)

        val restrictedDragSphere = Icosphere(Random.randomFromRange(0.04f, 0.2f), 2)
        restrictedDragSphere.material().diffuse = Vector3f(0f, 1.0f, 0f)
        scene.addChild(restrictedDragSphere)

        LineRestrictionEffector(restrictedDragSphere,{Vector3f(-1f,0f,0f)},{Vector3f(1f,0f,0f)})

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 1.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial().position = Vector3f(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)

            scene.addChild(this)
        }
    }

    override fun inputSetup() {
        super.inputSetup()

        val wiggle: (Scene.RaycastResult, Int, Int) -> Unit = { result, _, _ ->
            result.matches.firstOrNull()?.let { nearest ->
                val originalPosition = Vector3f(nearest.node.spatialOrNull()?.position)
                thread {
                    for (i in 0 until 200) {
                        nearest.node.spatialOrNull()?.position = originalPosition + Random.random3DVectorFromRange(-0.05f, 0.05f)
                        Thread.sleep(2)
                    }
                }
            }
        }

        renderer?.let { r ->
            // Left double-click - rotate object
            inputHandler?.addBehaviour(
                "leftClickRotate", MouseRotate(
                    "leftClickRotate",
                    { scene.findObserver() }, debugRaycast = false
                )
            )
            inputHandler?.addKeyBinding("leftClickRotate", "double-click button1")

            // Right double-click - wiggle object
            inputHandler?.addBehaviour(
                "rightClickWiggle", SelectCommand(
                    "rightClickWiggle", r, scene,
                    { scene.findObserver() }, action = wiggle, debugRaycast = false
                )
            )
            inputHandler?.addKeyBinding("rightClickWiggle", "double-click button3")
        }

        // Keyboard binding "1" - mimics left button (camera rotation)
        // Note: "1" is added as an additional trigger to the default mouse_control behavior
        inputHandler?.addKeyBinding("mouse_control", "1")

        // Keyboard binding "2" - mimics middle button (drag along plane)
        inputHandler?.addBehaviour(
            "planeDragObject", MouseDragPlane(
                "planeDragObject",
                { scene.findObserver() }, debugRaycast = false
            )
        )
        inputHandler?.addKeyBinding("planeDragObject", "2")

        // Keyboard binding "3" - mimics right button (drag along sphere)
        inputHandler?.addBehaviour(
            "sphereDragObject", MouseDragSphere(
                "sphereDragObject",
                { scene.findObserver() }, debugRaycast = false
            )
        )
        inputHandler?.addKeyBinding("sphereDragObject", "3")

        // Keyboard binding "4" - mimics left double-click (rotate object)
        inputHandler?.addBehaviour(
            "rotateObject", MouseRotate(
                "rotateObject",
                { scene.findObserver() }, debugRaycast = false
            )
        )
        inputHandler?.addKeyBinding("rotateObject", "4")

        // Keyboard binding "5" - mimics right double-click (wiggle object)
        renderer?.let { r ->
            inputHandler?.addBehaviour(
                "wiggleObject", SelectCommand(
                    "wiggleObject", r, scene,
                    { scene.findObserver() }, action = wiggle, debugRaycast = false
                )
            )
            inputHandler?.addKeyBinding("wiggleObject", "5")
        }

        // Additional mouse button bindings for testing cross-platform button mapping
        // Right button (button3) - drag along sphere
        inputHandler?.addBehaviour(
            "rightButtonSphereDrag", MouseDragSphere(
                "rightButtonSphereDrag",
                { scene.findObserver() }, debugRaycast = false
            )
        )
        inputHandler?.addKeyBinding("rightButtonSphereDrag", "button3")

        // Middle button (button2) - drag along plane
        inputHandler?.addBehaviour(
            "middleButtonPlaneDrag", MouseDragPlane(
                "middleButtonPlaneDrag",
                { scene.findObserver() }, debugRaycast = false
            )
        )
        inputHandler?.addKeyBinding("middleButtonPlaneDrag", "button2")

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MouseInputExample().main()
        }
    }
}
