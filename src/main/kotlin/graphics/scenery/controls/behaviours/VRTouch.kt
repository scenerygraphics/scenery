package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDevice
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import org.joml.Vector3f
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * Behavior for triggering actions when touching nodes.
 *
 * When [controllerHitbox] is intersecting a node with a [Touchable] attribute
 * [onTouch] and then the respective functions of the Touchable attribute are called.
 *
 * @param targets Only nodes in this list may be dragged. They must have a [onTouch] attribute.
 *
 * @author Jan Tiemann
 */
open class VRTouch(
    protected val name: String,
    protected val controllerHitbox: Node,
    protected val controller: TrackedDevice,
    protected val targets: () -> List<Node>,
    protected val onTouch: (() -> Unit)? = null
){

    var active = true

    init {

        // this has to be done in the post update otherwise the intersection test causes a stack overflow
        controllerHitbox.postUpdate.add {
            if(!active){
                if (selected.isNotEmpty()){
                    selected.forEach(::release)
                    selected = emptyList()
                }
                return@add
            }

            val hit = targets().filter { node ->
                controllerHitbox.spatialOrNull()?.intersects(node) ?: false
            }.toList()

            if(hit.isNotEmpty()){
                onTouch?.invoke()
            }

            val new = hit.filter { !selected.contains(it) }
            val released = selected.filter { !hit.contains(it) }
            selected = hit

            new.forEach{ node ->
                val touchable = node.getAttributeOrNull(Touchable::class.java)
                val material = node.materialOrNull()

                if (touchable != null) {
                    if (touchable.originalDiffuse == null
                        && touchable.changeDiffuseTo != null
                        && material != null) {
                        // if this is set some other VRTouch is already touching this
                        // and we dont want to interfere
                        touchable.originalDiffuse = material.diffuse
                        material.diffuse = touchable.changeDiffuseTo
                    }
                    touchable.onTouch?.invoke(controller)
                }
            }

            selected.forEach { node ->
                node.ifHasAttribute(Touchable::class.java){
                    this.onHold?.invoke(controller)
                }
            }

            released.forEach(::release)
        }
    }

    private fun release (node: Node) {
        val touchable = node.getAttributeOrNull(Touchable::class.java)
        val material = node.materialOrNull()

        if (touchable != null) {
            if (touchable.originalDiffuse != null && material != null) {
                material.diffuse = touchable.originalDiffuse!!
                touchable.originalDiffuse = null
            }
            touchable.onRelease?.invoke(controller)
        }
    }

    var selected = emptyList<Node>()

    /**
     * Contains Convenience method for adding touch behaviour
     */
    companion object {

        /**
         * Convenience method for adding touch behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            controllerSide: List<TrackerRole>,
            vibrate: Boolean
        ) : Future<VRTouch>{
            val future = CompletableFuture<VRTouch>()
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            val name = "VRDPress:${hmd.trackingSystemName}:${device.role}"
                            val touchBehaviour = VRTouch(
                                name,
                                controller.children.first(),
                                device,
                                { scene.discover(scene, { n -> n.getAttributeOrNull(Touchable::class.java) != null }) },
                                if (vibrate) fun(){ (hmd as? OpenVRHMD)?.vibrate(device) } else fun(){})
                            future.complete(touchBehaviour)
                        }
                    }
                }
            }
            return future
        }
    }
}

/**
 * Attribute Class that indicates an object can be touched with a controller.
 *
 * @param onTouch called in the first frame of the interaction
 * @param onHold called each frame of the interaction
 * @param onRelease called in the last frame of the interaction
 * @param changeDiffuseTo If set to null no color change will happen.
 */
open class Touchable(
    val onTouch: ((TrackedDevice) -> Unit)? = null,
    val onHold: ((TrackedDevice) -> Unit)? = null,
    val onRelease: ((TrackedDevice) -> Unit)? = null,
    val changeDiffuseTo: Vector3f? = Vector3f(1.0f, 0.0f, 0.0f)
) {
    /**
     * if this is set it means a touch is in progress and other [VRTouch] should not interfere
     * with the diffuse color
     */
    var originalDiffuse: Vector3f? = null
}
