package graphics.scenery.controls.behaviours

import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerInput
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.Wiggler
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
    var actions: List<Action>,
    val cutoff: Float = 0.1f,
) : DragBehaviour {
    protected val logger by LazyLogger(System.getProperty("scenery.LogLevel", "info"))

    private var activeWheel: WheelMenu? = null

    private var activeWiggler: Wiggler? = null

    override fun init(x: Int, y: Int) {

        activeWheel = WheelMenu(controller,hmd,actions)

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
            val action = closestActionSphere.action as? Action
            action?.action?.invoke()
        }

        activeWiggler?.deativate()
        activeWiggler = null

        activeWheel?.let { scene.removeChild(it) }
        activeWheel = null
    }

    companion object {

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
                            val name = "VRSelectionWheel:${hmd.trackingSystemName}:${device.role}:$button"
                            val vrToolSelector = VRSelectionWheel(
                                controller.children.first().spatialOrNull()
                                    ?: throw IllegalArgumentException("The target controller needs a spatial."),
                                scene,
                                hmd,
                                actions.map { Action(it.first, it.second) }
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
