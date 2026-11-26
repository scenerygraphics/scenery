package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenXRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plusAssign
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.lazyLogger
import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour

/** Move yourself (the scene camera) by pressing a VR button.
 * The fastest way to attach the behavior is by using [createAndSet].
 * You can pass a [grabButtonmanager] to handle multiple assignments per button.
 * @param onDrag hook to allow other behaviours to be executed at the same time. Provides the movement delta.
 * @author Jan Tiemann
 * @author Samuel Pantze */
class VRGrabTheWorld (
    @Suppress("UNUSED_PARAMETER") name: String,
    controllerHitbox: Node,
    private val cam: Spatial,
    private val grabButtonmanager: MultiButtonManager? = null,
    val button: OpenXRHMD.OpenXRButton,
    private val trackerRole: TrackerRole,
    private val multiplier: Float,
    private val onDrag: ((Vector3f) -> Unit)? = null,
) : DragBehaviour {

    private var camDiff = Vector3f()

    private val controllerSpatial: Spatial = controllerHitbox.spatialOrNull()
        ?: throw IllegalArgumentException("controller hitbox needs a spatial attribute")


    override fun init(x: Int, y: Int) {
        grabButtonmanager?.pressButton(button, trackerRole)
        camDiff = controllerSpatial.worldPosition() - cam.position
    }

    override fun drag(x: Int, y: Int) {
        val newCamDiff = controllerSpatial.worldPosition() - cam.position
        // Only drag when no other grab button is currently active
        // to prevent simultaneous behaviors with two-handed gestures
        if (grabButtonmanager?.isTwoHandedActive() != true) {
            //grabbed world
            val newCamDiff = controllerSpatial.worldPosition() - cam.position
            // reversed, since we move the world by moving ourselves the other way
            val applied = (camDiff - newCamDiff) * multiplier
            cam.position += applied
            onDrag?.invoke(applied)
        }

        camDiff = newCamDiff
    }

    override fun end(x: Int, y: Int) {
        grabButtonmanager?.releaseButton(button, trackerRole)
    }

    companion object {

        /**
         * Convenience method for adding grab behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenXRHMD,
            buttons: List<OpenXRHMD.OpenXRButton>,
            controllerSide: List<TrackerRole>,
            buttonManager: MultiButtonManager? = null,
            multiplier: Float = 1f,
            onDrag: ((Vector3f) -> Unit)? = null,
        ) {
            hmd.onDeviceConnect { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            buttons.forEach { button ->
                                val name = "VRDrag:${hmd.trackingSystemName}:${device.role}:$button"
                                val grabBehaviour = VRGrabTheWorld(
                                    name,
                                    controller.children.first(),
                                    scene.findObserver()!!.spatial(),
                                    buttonManager,
                                    button,
                                    device.role,
                                    multiplier,
                                    onDrag
                                )
                                buttonManager?.registerButtonConfig(button, device.role)
                                hmd.addBehaviour(name, grabBehaviour)
                                hmd.addKeyBinding(name, device.role, button)
                            }
                        }
                    }
                }
            }
        }
    }
}
