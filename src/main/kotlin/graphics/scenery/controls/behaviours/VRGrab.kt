package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
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
    protected val multiTarget: Boolean = false,
    protected val onGrab: (() -> Unit)? = null
) : DragBehaviour {

    val controllerSpatial: Spatial

    init {
        controllerSpatial = controllerHitbox.spatialOrNull()
            ?: throw IllegalArgumentException("controller hitbox needs a spatial attribute")
    }

    var selected = emptyList<Node>()

    var lastPos = Vector3f()
    var lastRotation = Quaternionf()

    override fun init(x: Int, y: Int) {
        selected = targets().filter { box -> controllerHitbox.spatialOrNull()?.intersects(box) ?: false }
        if (!multiTarget) {
            selected = selected.take(1)
        }
        if (selected.isNotEmpty()) {
            onGrab?.let { it() }
        }
        selected.forEach {it.getAttributeOrNull(Grabable::class.java)?.onGrab?.invoke()}
        lastPos = controllerSpatial.worldPosition()
        lastRotation = controllerSpatial.worldRotation()
    }

    override fun drag(x: Int, y: Int) {
        val newPos = controllerHitbox.spatialOrNull()?.worldPosition() ?: Vector3f()
        val diffTranslation = newPos - lastPos
        val diffRotation = Quaternionf(controllerSpatial.worldRotation()).mul(lastRotation.conjugate())

        selected.forEach {
            it.ifSpatial {
                it.getAttributeOrNull(Grabable::class.java)?.let { grabable ->

                    //apply parent world rotation to diff if available
                    position += it.parent?.spatialOrNull()?.worldRotation()?.let { q -> diffTranslation.rotate(q) }
                        ?: diffTranslation

                    if (!grabable.lockRotation) {
                        it.parent?.spatialOrNull()?.let { pSpatial ->
                            // if there is a parent spatial
                            // reverse parent rotation, apply diff rotation, apply parent rotation again
                            val worldRotationCache = pSpatial.worldRotation()
                            val newRotation = Quaternionf(worldRotationCache)
                                .mul(diffRotation)
                                .mul(worldRotationCache.conjugate())

                            rotation.premul(newRotation)
                        } ?: let {
                            rotation.premul(diffRotation)
                        }
                    }

                    grabable.onDrag?.invoke()
                }
            }
        }

        lastPos = controllerSpatial.worldPosition()
        lastRotation = controllerSpatial.worldRotation()
    }

    override fun end(x: Int, y: Int) {
        selected.forEach {it.getAttributeOrNull(Grabable::class.java)?.onRelease?.invoke()}
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
                            val name = "VRGrab:${hmd.trackingSystemName}:${device.role}:$button"
                            val grabBehaviour = VRGrab(
                                name,
                                controller.children.first(),
                                { scene.discover(scene, { n -> n.getAttributeOrNull(Grabable::class.java) != null }) },
                                false,
                                { (hmd as? OpenVRHMD)?.vibrate(device) })

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

open class Grabable(
    val lockRotation: Boolean = false,
    val onGrab: (() -> Unit)? = null,
    val onDrag: (() -> Unit)? = null,
    val onRelease: (() -> Unit)? = null){
}