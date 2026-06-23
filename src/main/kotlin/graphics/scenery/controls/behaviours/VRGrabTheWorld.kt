package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plusAssign
import graphics.scenery.utils.extensions.times
import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour

/** Move yourself (the scene camera) by pressing a VR button.
 * The fastest way to attach the behavior is by using [createAndSet].
 * You can pass a [grabButtonmanager] to handle multiple assignments per button.
 * @author Jan Tiemann
 * @author Samuel Pantze */
class VRGrabTheWorld (
    @Suppress("UNUSED_PARAMETER") name: String,
    controllerHitbox: Node,
    private val cam: Spatial,
    private val grabButtonmanager: MultiButtonManager? = null,
    val button: OpenVRHMD.OpenVRButton,
    private val trackerRole: TrackerRole,
    private val multiplier: Float
) : DragBehaviour {

    private var camDiff = Vector3f()

    private val controllerSpatial: Spatial = controllerHitbox.spatialOrNull()
        ?: throw IllegalArgumentException("controller hitbox needs a spatial attribute")


    override fun init(x: Int, y: Int) {
        grabButtonmanager?.pressButton(button, trackerRole)
        camDiff = controllerSpatial.worldPosition() - cam.position
    }

    override fun drag(x: Int, y: Int) {
        // Only drag when no other grab button is currently active
        // to prevent simultaneous behaviors with two-handed gestures
        if (grabButtonmanager?.isTwoHandedActive() != true) {
            //grabbed world
            val newCamDiff = controllerSpatial.worldPosition() - cam.position
            val diffTranslation = camDiff - newCamDiff //reversed
            cam.position += diffTranslation * multiplier
            camDiff = newCamDiff
        }
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
            hmd: OpenVRHMD,
            buttons: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>,
            buttonManager: MultiButtonManager? = null,
            multiplier: Float = 1f
        ) {
            hmd.events.onDeviceConnect.add { _, device, _ ->
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
                                    multiplier
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
