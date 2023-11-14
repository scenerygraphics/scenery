package graphics.scenery.controls.behaviours

import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerInput
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.Wiggler
import org.scijava.ui.behaviour.DragBehaviour
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * A fast selection wheel to let the user choose between different actions.
 *
 * Use the [createAndSet] method to create.
 *
 * The list of selectable actions can be changed dynamically.
 *
 * @param actions List of named lambdas which can be selected by the user
 * @param cutoff  after this distance between controller and targets no action will be selected if the button is released
 *
 * @author Jan Tiemann
 */
class VRSelectionWheel(
    val controller: Spatial,
    val scene: Scene,
    val hmd: TrackerInput,
    var actions: List<Action>,
    val cutoff: Float = 0.1f,
) : DragBehaviour {
    private var activeWheel: WheelMenu? = null

    private var activeWiggler: Wiggler? = null

    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun init(x: Int, y: Int) {

        activeWheel = WheelMenu(hmd, actions)
        activeWheel?.spatial()?.position = controller.worldPosition()

        scene.addChild(activeWheel!!)
    }

    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun drag(x: Int, y: Int) {

        val (closestSphere, distance) = activeWheel?.closestActionSphere(controller.worldPosition()) ?: return

        if (distance > cutoff) {
            activeWiggler?.deativate()
            activeWiggler = null

        } else if (activeWiggler?.target != closestSphere.representation) {
            activeWiggler?.deativate()
            activeWiggler = Wiggler(closestSphere.representation, 0.01f)
        }

    }

    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun end(x: Int, y: Int) {
        val (closestActionSphere, distance) = activeWheel?.closestActionSphere(controller.worldPosition()) ?: return

        if (distance < cutoff) {
            when(val entry = closestActionSphere.action){
                is Action -> entry.action()
                is Switch -> entry.toggle()
                else -> throw IllegalStateException("${entry.javaClass.simpleName} not implemented for Selection Wheel")
            }
        }

        activeWiggler?.deativate()
        activeWiggler = null

        activeWheel?.let { scene.removeChild(it) }
        activeWheel = null
    }

    /**
     * Contains Convenience method for adding tool select behaviour
     */
    companion object {

        /**
         * Convenience method for adding tool select behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            button: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>,
            actions: List<Pair<String, (Spatial) -> Unit>>,
        ): Future<VRSelectionWheel> {
            val future = CompletableFuture<VRSelectionWheel>()
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            val name = "VRSelectionWheel:${hmd.trackingSystemName}:${device.role}:$button"
                            val vrToolSelector = VRSelectionWheel(
                                controller.children.first().spatialOrNull()
                                    ?: throw IllegalArgumentException("The target controller needs a spatial."),
                                scene,
                                hmd,
                                actions.toActions(
                                    controller.children.first().spatialOrNull() ?: throw IllegalArgumentException(
                                        "The target controller needs a spatial."
                                    )
                                )
                            )
                            hmd.addBehaviour(name, vrToolSelector)
                            button.forEach {
                                hmd.addKeyBinding(name, device.role, it)
                            }
                            future.complete(vrToolSelector)
                        }
                    }
                }
            }
            return future
        }

        /**
         * Convert lambdas to wheel menu action.
         */
        fun List<Pair<String, () -> Unit>>.toActions(): List<Action> = map { Action(it.first, action = it.second)}

        /**
         * Convert lambdas which take the spatial of the controller as an argument to wheel menu action.
         */
        fun List<Pair<String, (Spatial) -> Unit>>.toActions(device: Spatial): List<Action> =
            map { Action(it.first) { it.second.invoke(device) } }

    }
}

