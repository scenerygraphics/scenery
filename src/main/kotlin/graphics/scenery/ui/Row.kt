package graphics.scenery.ui

import graphics.scenery.Mesh
import graphics.scenery.OrientedBoundingBox
import graphics.scenery.RichNode
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Vector3f
import kotlin.concurrent.thread

/**
 * Elements can be changed via scene graph at runtime.
 * @author Jan Tiemann
 * @author Samuel Pantze
 */
open class Row(
    vararg elements: Gui3DElement,
    val margin: Float = 0.5f,
    var middleAlign: Boolean = true
) : Mesh("UI Row"), Gui3DElement {

    final override var width = 0f
        private set
    final override var height = 0f
        private set

    /** Callbacks to execute once the row has been packed with valid geometry. */
    private val onPackedCallbacks = mutableListOf<() -> Unit>()

    /** Flag to track if we've already executed onPacked callbacks. */
    private var hasExecutedCallbacks = false

    init {
        elements.forEach { this.addChild(it) }

        thread {
            while (!hasExecutedCallbacks) {
                val uiChildren = children.filterIsInstance<Gui3DElement>()
                val hasValidWidths = uiChildren.isNotEmpty() && uiChildren.all { it.width > 0f }

                if (hasValidWidths) {
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

    /** Register a callback to be executed once this row has been packed with valid geometry. */
    fun onGeometryReady(callback: () -> Unit) {
        if (hasExecutedCallbacks) {
            // Geometry already ready, execute immediately
            callback()
        } else {
            onPackedCallbacks.add(callback)
        }
    }

    fun pack() {
        val uiChildren = children.filterIsInstance<Gui3DElement>()
        val currentWidth = uiChildren.sumOf { it.width.toDouble() }.toFloat() + (uiChildren.size - 1) * margin
        if (currentWidth != width && uiChildren.isNotEmpty()){
            width = currentWidth
            var indexWidth = 0f
            uiChildren.forEach {
                it.spatial {
                    position.x = indexWidth
                    needsUpdate = true
                }
                indexWidth += it.width + margin
            }
            if (middleAlign){
                indexWidth -= margin
                spatial {
                    position.x = indexWidth * -0.5f
                    needsUpdate = true
                }
            }
            height = uiChildren.maxOf { it.height }

            this.generateBoundingBox()
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
