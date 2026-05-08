package graphics.scenery.ui

import graphics.scenery.Mesh
import graphics.scenery.OrientedBoundingBox
import graphics.scenery.Origin
import graphics.scenery.utils.extensions.plus
import graphics.scenery.ui.Gui3DElement.Anchor
import org.joml.Vector3f
import kotlin.concurrent.thread

/**
 * Rows cousin. Anchor is bottom middle. Elements can be changed via scene graph at runtime.
 *
 * @param centerVertically also align vertically to middle
 * @param
 * @param invertedYOrder reverse order of elements - last child will be the top most
 * @author Jan Tiemann
 * @author Samuel Pantze
 */
open class Column(
    vararg elements: Gui3DElement,
    val margin: Float = 0.2f,
    var centerVertically: Boolean = false,
    var centerHorizontally: Boolean = false,
    val invertedYOrder: Boolean = true,
    val anchor: Anchor = Anchor.Top,
) : Mesh("UI Column"), Gui3DElement {

    final override var width = 0f
        private set
    final override var height = 0f
        private set

    /** Callbacks to execute once the column has been packed with valid geometry. */
    private val onPackedCallbacks = mutableListOf<() -> Unit>()

    /** Flag to track if we've already executed onPacked callbacks. */
    private var hasExecutedCallbacks = false


    init {
        elements.forEach { this.addChild(it) }

        thread {
            while (!hasExecutedCallbacks) {
                val uiChildren = children.filterIsInstance<Gui3DElement>()
                // We're still checking widths here, since heights are predefined, and widths tell us when
                // the text geometry was initialized
                val hasValidDimensions = uiChildren.isNotEmpty() && uiChildren.all { it.width > 0f }
                if (hasValidDimensions) {
                    pack()
                    hasExecutedCallbacks = true
                    onPackedCallbacks.forEach { it() }
                    break
                } else {
                    Thread.sleep(20)
                }
            }
        }
    }

    /** Register a callback to be executed once this column contains valid geometry. */
    fun onGeometryReady(callback: () -> Unit) {
        if (hasExecutedCallbacks) {
            // Geometry already ready, execute immediately
            callback()
        } else {
            onPackedCallbacks.add(callback)
        }
    }

    fun pack() {
        var uiChildren = children.filter { it.visible }.filterIsInstance<Gui3DElement>()
        if (invertedYOrder) uiChildren = uiChildren.reversed()
        val currentHeight = uiChildren.sumOf { it.height.toDouble() }.toFloat() + (uiChildren.size - 1) * margin
        if (currentHeight != height && uiChildren.isNotEmpty()) {
            height = currentHeight
            var indexHeight = 0f
            uiChildren.forEach {
                it.spatial {
                    position.x = if (centerHorizontally) it.width * -0.5f else 0f
                    position.y = indexHeight
                    needsUpdate = true
                }
                indexHeight += it.height + margin
            }

            this.generateBoundingBox()

            spatial {
                // Columns are typically scaled isotropically, so we can assume that scale.x is representative
                position.z = if (centerVertically) (indexHeight - margin) * scale.x() * 0.5f else 0f

                boundingBox?.let { bb ->
                    origin = Origin.Custom(
                        when (anchor) {
                            Anchor.Top -> Vector3f(bb.localCenter.x, bb.max.y, bb.localCenter.z)
                            Anchor.Bottom -> Vector3f(bb.localCenter.x, bb.min.y, bb.localCenter.z)
                            Anchor.Left -> Vector3f(bb.min.x, bb.localCenter.y, bb.localCenter.z)
                            Anchor.Right -> Vector3f(bb.max.x, bb.localCenter.y, bb.localCenter.z)
                            Anchor.Center -> Vector3f(bb.localCenter)
                        }
                    )
                }
                needsUpdate = true
            }
            width = uiChildren.maxOf { it.width }
        }
    }

    override fun generateBoundingBox(includeChildren: Boolean): OrientedBoundingBox? {
        val localMin = Vector3f(Float.POSITIVE_INFINITY)
        val localMax = Vector3f(Float.NEGATIVE_INFINITY)
        // Iteratively expand the min/max values of this BB based on the column's children
        children.filterIsInstance<Gui3DElement>().forEach { element ->
            element.boundingBox?.let { bb ->
                localMin.min(bb.min + element.spatial().position)
                localMax.max(bb.max + element.spatial().position)
            }
        }
        boundingBox = if(includeChildren) OrientedBoundingBox(this, localMin, localMax) else null
        return boundingBox
    }

    override fun getMaximumBoundingBox(): OrientedBoundingBox {
        val bb = boundingBox ?: generateBoundingBox(true)
        return bb ?: OrientedBoundingBox(this, Vector3f(0f), Vector3f(0f))
    }
}

