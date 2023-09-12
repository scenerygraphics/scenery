package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.attribute.spatial.HasSpatial
import org.joml.Vector3f

/**
 * Behavior for triggering actions when touching/colliding with nodes.
 *
 * When [toucher] is intersecting a node with a [Touchable] attribute
 * [onTouch] and then the respective functions of the Touchable attribute are called.
 *
 * @param targets Only nodes in this list may be touched. They must have a [onTouch] attribute.
 *
 * @author Jan Tiemann
 */
open class Touch(
    protected val name: String,
    protected val toucher: HasSpatial,
    protected val targets: () -> List<Node>,
    protected val onTouch: (() -> Unit)? = null
) {
    var active = true
    var selected = emptyList<Node>()

    init {
        // this has to be done in the post update otherwise the intersection test causes a stack overflow
        toucher.postUpdate.add {
            if (!active) {
                if (selected.isNotEmpty()) {
                    selected.forEach {
                        unapplySelectionColor(it)
                        it.getAttributeOrNull(Touchable::class.java)?.onRelease?.invoke(toucher)
                    }
                    selected = emptyList()
                }
                return@add
            }

            val hit = targets().filter { node ->
                toucher.spatialOrNull()?.intersects(node,true) ?: false
            }.toList()

            if (hit.isNotEmpty()) {
                onTouch?.invoke()
            }

            val new = hit.filter { !selected.contains(it) }
            val released = selected.filter { !hit.contains(it) }
            selected = hit
            new.forEach {
                applySelectionColor(it)
                it.getAttributeOrNull(Touchable::class.java)?.onTouch?.invoke(toucher)
            }

            selected.forEach { node ->
                node.ifHasAttribute(Touchable::class.java) {
                    this.onHold?.invoke(toucher)
                }
            }

            released.forEach {
                unapplySelectionColor(it)
                it.getAttributeOrNull(Touchable::class.java)?.onRelease?.invoke(toucher)
            }
        }
    }

    /**
     * Contains Convenience method for adding touch behaviour
     */
    companion object {

        /**
         * Apply the [Touchable.onHoldChangeDiffuseTo] color.
         * If you are calling this manually make sure [unapplySelectionColor] will be called later.
         */
        fun applySelectionColor(node: Node) {
            val touchable = node.getAttributeOrNull(Touchable::class.java)
            val material = node.materialOrNull()

            // if the following is set, some other VRTouch is already touching this
            // and we don't want to interfere
            if (touchable?.originalDiffuse == null
                && touchable?.onHoldChangeDiffuseTo != null
                && material != null
            ) {
                touchable.originalDiffuse = material.diffuse
                material.diffuse = touchable.onHoldChangeDiffuseTo
            }
        }

        /**
         * Return to the original diffuse color after calling  [applySelectionColor].
         * Should do nothing if no previous call of [applySelectionColor] happend.
         */
        fun unapplySelectionColor(node: Node) {
            val touchable = node.getAttributeOrNull(Touchable::class.java)
            val material = node.materialOrNull()

            if (touchable?.originalDiffuse != null && material != null) {
                material.diffuse = touchable.originalDiffuse!!
                touchable.originalDiffuse = null
            }
        }
    }
}

/**
 * Attribute Class that indicates an object can be touched with a controller.
 *
 * @param onTouch called in the first frame of the interaction
 * @param onHold called each frame of the interaction
 * @param onRelease called in the last frame of the interaction
 * @param onHoldChangeDiffuseTo If set to null no color change will happen.
 */
open class Touchable(
    val onTouch: ((HasSpatial) -> Unit)? = null,
    val onHold: ((HasSpatial) -> Unit)? = null,
    val onRelease: ((HasSpatial) -> Unit)? = null,
    val onHoldChangeDiffuseTo: Vector3f? = Vector3f(1.0f, 0.0f, 0.0f)
) {
    /**
     * if this is set it means a touch is in progress and other [VRTouch] should not interfere
     * with the diffuse color
     */
    var originalDiffuse: Vector3f? = null
}
