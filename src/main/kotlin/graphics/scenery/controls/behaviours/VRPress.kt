package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import org.scijava.ui.behaviour.DragBehaviour

/**
 * Behavior for pressing or clicking nodes.
 *
 * When triggered and [controllerHitbox] is intersecting a node with a [Pressable] attribute
 * [onPress] and then the respective functions of the Pressable attribute are called.
 *
 * @param targets Only nodes in this list may be dragged. They must have a [Pressable] attribute.
 * @param multiTarget If this is true all targets which collide with [controllerHitbox] will be interacted with otherwise only one.
 *
 * @author Jan Tiemann
 */
open class VRPress(
    protected val name: String,
    protected val controllerHitbox: Node,
    protected val targets: () -> List<Node>,
    protected val multiTarget: Boolean = false,
    protected val onPress: ((Node) -> Unit)? = null
) : DragBehaviour {

    protected val controllerSpatial: Spatial = controllerHitbox.spatialOrNull()
        ?: throw IllegalArgumentException("controller hitbox needs a spatial attribute")

    protected var selected = emptyList<Node>()

    /**
     * Called on the first frame this behavior is triggered.
     *
     * @param x invalid - residue from parent behavior. Use [controllerSpatial] instead.
     * @param y invalid - residue from parent behavior. Use [controllerSpatial] instead.
     */
    override fun init(x: Int, y: Int) {
        selected = targets().filter { box -> controllerHitbox.spatialOrNull()?.intersects(box) ?: false }
        if (!multiTarget) {
            selected = selected.take(1)
        }
        selected.forEach { node ->
            onPress?.let { it(node) }
            node.getAttributeOrNull(Pressable::class.java)?.onPress?.invoke()
        }
    }

    /**
     * Called on every frame this behavior is triggered.
     *
     * @param x invalid - residue from parent behavior. Use [controllerSpatial] instead.
     * @param y invalid - residue from parent behavior. Use [controllerSpatial] instead.
     */
    override fun drag(x: Int, y: Int) {
        selected.forEach { it.getAttributeOrNull(Pressable::class.java)?.onHold?.invoke() }
    }

    /**
     * Called on the last frame this behavior is triggered.
     *
     * @param x invalid - residue from parent behavior. Use [controllerSpatial] instead.
     * @param y invalid - residue from parent behavior. Use [controllerSpatial] instead.
     */
    override fun end(x: Int, y: Int) {
        selected.forEach { it.getAttributeOrNull(Pressable::class.java)?.onRelease?.invoke() }
        selected = emptyList()
    }

    /**
     * Contains Convenience method for adding press behaviour
     */
    companion object {

        /**
         * Convenience method for adding press behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            button: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>,
            onPress: ((Node) -> Unit)? = null
        ) {
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            val name = "VRDPress:${hmd.trackingSystemName}:${device.role}:$button"
                            val pressBehaviour = VRPress(
                                name,
                                controller.children.first(),
                                { scene.discover(scene, { n -> n.getAttributeOrNull(Pressable::class.java) != null }) },
                                false,
                                {
                                    (hmd as? OpenVRHMD)?.vibrate(device)
                                    onPress?.invoke(it)
                                })

                            hmd.addBehaviour(name, pressBehaviour)
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

/**
 * Attribute which marks a node than can be pressed by the [VRPress] behavior.
 *
 * @param onPress called in the first frame of the interaction
 * @param onHold called each frame of the interaction
 * @param onRelease called in the last frame of the interaction
 */
open class Pressable(
    val onPress: (() -> Unit)? = null,
    val onHold: (() -> Unit)? = null,
    val onRelease: (() -> Unit)? = null
)
