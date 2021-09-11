package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import org.scijava.ui.behaviour.DragBehaviour

open class VRPress (
    protected val name: String,
    protected val controllerHitbox: Node,
    protected val targets: () -> List<Node>,
    protected val multiTarget: Boolean = false,
    protected val onPress: (() -> Unit)? = null
) : DragBehaviour {

    val controllerSpatial: Spatial

    init {
        controllerSpatial = controllerHitbox.spatialOrNull()
            ?: throw IllegalArgumentException("controller hitbox needs a spatial attribute")
    }

    var selected = emptyList<Node>()

    override fun init(x: Int, y: Int) {
        selected = targets().filter { box -> controllerHitbox.spatialOrNull()?.intersects(box) ?: false }
        if (!multiTarget) {
            selected = selected.take(1)
        }
        if (selected.isNotEmpty()) {
            onPress?.let { it() }
        }
        selected.forEach {it.getAttributeOrNull(Pressable::class.java)?.onPress?.invoke()}
    }

    override fun drag(x: Int, y: Int) {
        selected.forEach {it.getAttributeOrNull(Pressable::class.java)?.onHold?.invoke()}
    }

    override fun end(x: Int, y: Int) {
        selected.forEach {it.getAttributeOrNull(Pressable::class.java)?.onRelease?.invoke()}
        selected = emptyList()
    }
    companion object {

        /**
         * Convenience method for adding press behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            button: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>
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
                                { (hmd as? OpenVRHMD)?.vibrate(device) })

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


open class Pressable(
    val onPress: (() -> Unit)? = null,
    val onHold: (() -> Unit)? = null,
    val onRelease: (() -> Unit)? = null){
}
