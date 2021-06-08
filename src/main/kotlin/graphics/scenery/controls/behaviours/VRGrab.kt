package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour


/**
 * Grab and Drag nodes with a VR controller.
 *
 * @param targets Only nodes in this list may be dragged. They must have a [Grabable] attribute.
 * @param multiTarget If this is true all targets which collide with [controllerHitbox] will be dragged otherwise only one.
 *
 * @author Jan Tiemann
 */
open class VRGrab(
    protected val name: String,
    protected val controllerHitbox: Node,
    protected val targets: () -> List<Node>,
    protected val multiTarget: Boolean = false
) : DragBehaviour {

    var selected = emptyList<Node>()
    var startPos = Vector3f()

    override fun init(x: Int, y: Int) {
        selected = targets().filter { box -> controllerHitbox.spatialOrNull()?.intersects(box) ?: false }
        if (!multiTarget) {
            selected = selected.take(1)
        }
        selected.forEach {
            it.getAttributeOrNull(Grabable::class.java)?.let { grabable ->
                it.ifSpatial {
                    grabable.startPos = position

                    grabable.rotationDiff = Quaternionf()
                    controllerHitbox.spatialOrNull()?.worldRotation()?.invert(grabable.rotationDiff)
                    grabable.rotationDiff?.mul(rotation)
                }
            }
        }
        startPos = controllerHitbox.spatialOrNull()?.worldPosition() ?: Vector3f()
    }

    override fun drag(x: Int, y: Int) {
        val newPos = controllerHitbox.spatialOrNull()?.worldPosition() ?: Vector3f()
        val diff = newPos - startPos


        selected.forEach {
            it.ifSpatial {
                it.getAttributeOrNull(Grabable::class.java)?.let { grabable ->
                    position = grabable.startPos + diff

                    if (!grabable.lockRotation) {
                        rotation = Quaternionf(grabable.rotationDiff)
                        rotation.premul(controllerHitbox.spatialOrNull()?.worldRotation())
                    }
                }
            }
        }
    }

    override fun end(x: Int, y: Int) {
        selected = emptyList()
    }

    companion object {

        /**
         * Convenience method for adding grab behaviour
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
                            val name = "VRDrag:${hmd.trackingSystemName}:${controllerSide}"
                            val grabBehaviour = VRGrab(
                                name,
                                controller.children.first(),
                                { scene.discover(scene, { n -> n.getAttributeOrNull(Grabable::class.java) != null }) })

                            hmd.addBehaviour(name, grabBehaviour)
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

open class Grabable(val lockRotation: Boolean = false) {
    internal var startPos = Vector3f()
    internal var rotationDiff: Quaternionf? = null
}
