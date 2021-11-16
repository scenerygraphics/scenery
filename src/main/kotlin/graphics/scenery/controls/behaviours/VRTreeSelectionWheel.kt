package graphics.scenery.controls.behaviours

import graphics.scenery.Scene
import graphics.scenery.Sphere
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerInput
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.plusAssign
import graphics.scenery.utils.extensions.times
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour

/**
 * A selection wheel to let the user choose between different actions.
 * The selection is done by [VRPress]. Therefore it is required for this behavior.
 *
 * The list of selectable actions can be changed dynamically.
 *
 * @param actions List of named lambdas which can be selected by the user
 * @param cutoff  after this distance between controller and targets no action will be selected if the button is released
 */
class VRTreeSelectionWheel(
    val controller: Spatial,
    val scene: Scene,
    val hmd: TrackerInput,
    var actions: List<WheelAction>,
    val cutoff: Float = 0.1f,
) : ClickBehaviour {
    protected val logger by LazyLogger(System.getProperty("scenery.LogLevel", "info"))

    private var activeWheel: WheelMenu? = null

    override fun click(x: Int, y: Int) {

        if (activeWheel != null && activeWheel?.parent != null){
            // if the previous wheel was closed by the close button is is still set here but has no parent
            closeWheel(activeWheel!!,true)
            activeWheel = null
        } else {
            activeWheel = WheelMenu(controller, hmd, actions, true)

            scene.addChild(activeWheel!!)
        }
    }

    companion object {

        private data class ActionSphere(val action: WheelAction, val sphere: Sphere)

        /**
         * Convenience method for adding tool select behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            button: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>,
            menu: List<WheelAction>,
        ) {
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            val name = "VRTreeSelectionWheel:${hmd.trackingSystemName}:${device.role}:$button"
                            val vrToolSelector = VRTreeSelectionWheel(
                                controller.children.first().spatialOrNull()
                                    ?: throw IllegalArgumentException("The target controller needs a spatial."),
                                scene,
                                hmd,
                                menu
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

        internal fun openSubWheel(new: WheelMenu, old: WheelMenu, relActionSpherePos: Vector3f){
            val root = old.parent?: return

            root.removeChild(old)
            root.addChild(new)
            new.addChild(old)

            new.previous = old

            new.spatial().position = old.spatial().position
            old.spatial().position = relActionSpherePos * -1.0f
            old.spatial().position += Vector3f(0f,0f,-0.15f)

            old.followHead = false
            old.spatial().rotation = Quaternionf()
        }

        internal fun closeWheel(wheel: WheelMenu, recursive: Boolean = false){
            if (wheel.previous == null){
                wheel.parent?.removeChild(wheel)
                return
            }

            val root = wheel.parent?: return
            val prev = wheel.previous!!

            root.removeChild(wheel)
            wheel.removeChild(prev)
            root.addChild(prev)

            prev.spatial().position = wheel.spatial().position
            prev.followHead = true

            if (recursive){
                closeWheel(prev,true)
            }
        }
    }
}
