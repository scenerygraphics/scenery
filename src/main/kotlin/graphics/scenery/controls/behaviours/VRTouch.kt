package graphics.scenery.controls.behaviours

import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.HasSpatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.VRTouch.createAndSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * Quasi VR behavior for triggering actions when touching nodes.
 *
 * Use the [createAndSet] method to create.
 *
 *
 * @author Jan Tiemann
 */
object VRTouch {
    /**
     * Convenience method for adding touch behaviour
     *
     * When controller is intersecting a node with a [Touchable] attribute
     * [onTouch] and then the respective functions of the Touchable attribute are called.
     */
    fun createAndSet(
        scene: Scene,
        hmd: OpenVRHMD,
        controllerSide: List<TrackerRole>,
        vibrate: Boolean,
        onTouch: (() -> Unit)? = null
    ): Future<Touch> {
        val future = CompletableFuture<Touch>()
        hmd.events.onDeviceConnect.add { _, device, _ ->
            if (device.type == TrackedDeviceType.Controller) {
                device.model?.let { controller ->
                    if (controllerSide.contains(device.role)) {
                        val name = "VRDPress:${hmd.trackingSystemName}:${device.role}"
                        val touchBehaviour = Touch(
                            name,
                            (controller.children.firstOrNull { it.name == "collider"}?: controller.children.first()) as HasSpatial,
                            { scene.discover(scene, { n -> n.getAttributeOrNull(Touchable::class.java) != null }) },
                            if (vibrate) fun() { (hmd as? OpenVRHMD)?.vibrate(device); onTouch?.invoke() } else onTouch)
                        future.complete(touchBehaviour)
                    }
                }
            }
        }
        return future
    }
}

