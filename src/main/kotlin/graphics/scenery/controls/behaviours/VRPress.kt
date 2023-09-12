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
 * Use the [createAndSet] method to create.
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
    protected val button: OpenVRHMD.OpenVRButton,
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
        selected = targets().filter { box -> controllerHitbox.spatialOrNull()?.intersects(box, true) ?: false }
        if (!multiTarget) {
            selected = selected.take(1)
        }
        selected.forEach { node ->
            onPress?.let { it(node) }
            node.ifHasAttribute(Pressable::class.java) {
                when (this) {
                    is SimplePressable -> this
                    is PerButtonPressable -> {
                        this.actions[button]
                    }
                }?.onPress?.invoke(controllerSpatial)
            }
        }
    }

    /**
     * Called on every frame this behavior is triggered.
     *
     * @param x invalid - residue from parent behavior. Use [controllerSpatial] instead.
     * @param y invalid - residue from parent behavior. Use [controllerSpatial] instead.
     */
    override fun drag(x: Int, y: Int) {
        selected.forEach {
            it.ifHasAttribute(Pressable::class.java) {
                when (this) {
                    is SimplePressable -> this
                    is PerButtonPressable -> {
                        this.actions[button]
                    }
                }?.onHold?.invoke(controllerSpatial)
            }
        }
    }

    /**
     * Called on the last frame this behavior is triggered.
     *
     * @param x invalid - residue from parent behavior. Use [controllerSpatial] instead.
     * @param y invalid - residue from parent behavior. Use [controllerSpatial] instead.
     */
    override fun end(x: Int, y: Int) {
        selected.forEach {
            it.ifHasAttribute(Pressable::class.java) {
                when (this) {
                    is SimplePressable -> this
                    is PerButtonPressable -> {
                        this.actions[button]
                    }
                }?.onRelease?.invoke(controllerSpatial)
            }
        }
        selected = emptyList()
    }

    /**
     * Contains Convenience method for adding press behaviour
     */
    companion object {

        /**
         * Convenience method for adding press behaviour.
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            buttons: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>,
            onPress: ((Node, OpenVRHMD.OpenVRButton) -> Unit)? = null
        ) {
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            buttons.forEach { button ->
                                val name = "VRDPress:${hmd.trackingSystemName}:${device.role}:$button"
                                val pressBehaviour = VRPress(
                                    name,
                                    controller.children.firstOrNull { it.name == "collider"}?: controller.children.first(),
                                    {
                                        scene.discover(
                                            scene,
                                            { n -> n.getAttributeOrNull(Pressable::class.java) != null })
                                    },
                                    button,
                                    false,
                                ) {
                                    (hmd as? OpenVRHMD)?.vibrate(device)
                                    onPress?.invoke(it, button)
                                }

                                hmd.addBehaviour(name, pressBehaviour)
                                hmd.addKeyBinding(name, device.role, button)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top level class for press able nodes in VR interaction.
 *
 *  See [SimplePressable] for functionality that was previously implemented under this name.
 */
sealed class Pressable

/**
 * Attribute which marks a node than can be pressed by the [VRPress] behavior.
 *
 * Each action receives the [Spatial] of the pressing controller
 *
 * @param onPress called in the first frame of the interaction
 * @param onHold called each frame of the interaction
 * @param onRelease called in the last frame of the interaction
 */
open class SimplePressable(
    open val onPress: ((Spatial) -> Unit)? = null,
    open val onHold: ((Spatial) -> Unit)? = null,
    open val onRelease: ((Spatial) -> Unit)? = null
) : Pressable()

/**
 * Like [SimplePressable] but for each button.
 */
open class PerButtonPressable(open val actions: Map<OpenVRHMD.OpenVRButton, SimplePressable>) : Pressable()
