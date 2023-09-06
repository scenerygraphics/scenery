package graphics.scenery.controls.behaviours

import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerInput
import graphics.scenery.controls.TrackerRole
import org.scijava.ui.behaviour.ClickBehaviour
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * A selection wheel to let the user choose between different actions.
 *
 * Use the [createAndSet] method to create.
 *
 * The selection is done by [VRPress]. Therefore it is required for this behavior.
 *
 * The list of selectable actions can be changed dynamically.
 *
 * @param actions List of named lambdas which can be selected by the user
 * @param cutoff  after this distance between controller and targets no action will be selected if the button is released
 *
 * @author Jan Tiemann
 */
class VRTreeSelectionWheel(
    val controller: Spatial,
    val scene: Scene,
    val hmd: TrackerInput,
    var actions: List<WheelEntry>,
    val cutoff: Float = 0.1f,
) : ClickBehaviour {

    private var activeWheel: WheelMenu? = null

    /**
     * This function is called by the framework. Usually you don't need to call this.
     */
    override fun click(x: Int, y: Int) {

        if (activeWheel != null && activeWheel?.parent != null){
            // if the previous wheel was closed by the close button is still set here but has no parent
            activeWheel?.closeWheel(true)
            activeWheel = null
        } else {
            activeWheel = WheelMenu(hmd, actions, true)
            activeWheel?.spatial()?.position = controller.worldPosition()

            scene.addChild(activeWheel!!)
        }
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
            menu: List<WheelEntry>,
        ) : Future<VRTreeSelectionWheel> {
            val future = CompletableFuture<VRTreeSelectionWheel>()
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            val name = "VRTreeSelectionWheel:${hmd.trackingSystemName}:${device.role}:$button"
                            val vrToolSelector = VRTreeSelectionWheel(
                                (controller.children.firstOrNull { it.name == "collider"}?: controller.children.first()).spatialOrNull()
                                    ?: throw IllegalArgumentException("The target controller needs a spatial."),
                                scene,
                                hmd,
                                menu
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
    }
}
