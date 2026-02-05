package graphics.scenery.ui

import graphics.scenery.RichNode
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
    val invertedYOrder: Boolean = true
) : RichNode("UI Column"), Gui3DElement {

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

            spatial {
                // Columns are typically scaled isotropically, so we can assume that scale.x is representative
                position.z += height * scale.x()
                logger.info("menu height is $height")
                position.z += if (centerVertically) (indexHeight - margin) * scale.x() * 0.5f else 0f
                needsUpdate = true
            }

            width = uiChildren.maxOf { it.width }
        }
    }
}

