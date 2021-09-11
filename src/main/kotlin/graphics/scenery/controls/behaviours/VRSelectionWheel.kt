package graphics.scenery.controls.behaviours

import graphics.scenery.RichNode
import graphics.scenery.Scene
import graphics.scenery.Sphere
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerInput
import graphics.scenery.controls.TrackerRole
import graphics.scenery.primitives.TextBoard
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.Wiggler
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.scijava.ui.behaviour.DragBehaviour

/**
 * A selection wheel to let the user choose between different actions.
 *
 * The list of selectable actions can be changed dynamically.
 *
 * @param actions List of named lambdas which can be selected by the user
 * @param cutoff  after this distance between controller and targets no action will be selected if the button is released
 */
class VRSelectionWheel(
    val controller: Spatial,
    val scene: Scene,
    val hmd: TrackerInput,
    var actions: List<Pair<String, () -> Unit>>,
    val cutoff: Float = 0.1f
) : DragBehaviour {
    protected val logger by LazyLogger(System.getProperty("scenery.LogLevel", "info"))

    private var activeWheel: Wheel? = null

    private var activeWiggler: Wiggler? = null

    override fun init(x: Int, y: Int) {

        activeWheel = Wheel(controller,hmd,actions)

        scene.addChild(activeWheel!!)
    }

    override fun drag(x: Int, y: Int) {

        val (closestSphere, distance) = activeWheel?.closestActionSphere() ?: return

        if (distance > cutoff) {
            activeWiggler?.deativate()
            activeWiggler = null

        } else if (activeWiggler?.target != closestSphere.sphere.spatial()) {
            activeWiggler?.deativate()
            activeWiggler = Wiggler(closestSphere.sphere.spatial(), 0.01f)
        }

    }

    override fun end(x: Int, y: Int) {
        val (closestActionSphere, distance) = activeWheel?.closestActionSphere() ?: return

        if (distance < cutoff) {
            closestActionSphere.action()
        }

        activeWiggler?.deativate()
        activeWiggler = null

        activeWheel?.let { scene.removeChild(it) }
        activeWheel = null
    }


    private class Wheel(
        val controller: Spatial,
        val hmd: TrackerInput,
        var actions: List<Pair<String, () -> Unit>>
        ) : RichNode("Selection Wheel"){

        val actionSpheres : List<ActionSphere>

        init {
            spatial {
                position = controller.worldPosition()
            }

            update.add {
                spatial {
                    rotation = Quaternionf(hmd.getOrientation()).conjugate().normalize()
                }
            }

            actionSpheres = actions.mapIndexed { index, action ->
                val pos = Vector3f(0f, .15f, 0f)
                pos.rotateZ((2f * Math.PI.toFloat() / actions.size) * index)

                val sphereRoot = RichNode()
                addChild(sphereRoot)
                sphereRoot.spatial().position = pos

                val sphere = Sphere(0.025f, 10)
                sphereRoot.addChild(sphere)

                val board = TextBoard()
                board.text = action.first
                board.name = "ToolSelectTextBoard"
                board.transparent = 0
                board.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
                board.backgroundColor = Vector4f(100f, 100f, 100f, 1.0f)
                board.spatial {
                    position = Vector3f(0f, 0.05f, 0f)
                    scale = Vector3f(0.05f, 0.05f, 0.05f)
                }
                sphereRoot.addChild(board)

                ActionSphere(action.first, action.second, sphere)
            }
        }

        /**
         * @return (closest actionSphere) to (distance to controller)
         */
        fun closestActionSphere() = actionSpheres.map { entry ->
            entry to entry.sphere.spatial().worldPosition().distance(controller.worldPosition())
        }.reduceRight { left, right -> if (left.second < right.second) left else right }
    }

    companion object {

        private data class ActionSphere(val name: String, val action: () -> Unit, val sphere: Sphere)

        /**
         * Convenience method for adding tool select behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            button: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>,
            actions: List<Pair<String, () -> Unit>>,
        ) {
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            val name = "VRDrag:${hmd.trackingSystemName}:${device.role}:$button"
                            val vrToolSelector = VRSelectionWheel(
                                controller.children.first().spatialOrNull()
                                    ?: throw IllegalArgumentException("The target controller needs a spatial."),
                                scene,
                                hmd,
                                actions
                            )
                            hmd.addBehaviour(name, vrToolSelector)
                            button.forEach {
                                hmd.addKeyBinding(name, device.role, it)
                            }
                        }
                    }
                }
            }
        }
    }
}