package graphics.scenery.ui

import graphics.scenery.RichNode
import org.joml.Vector3f

/**
 * Rows cousin. Anchor is bottom middle. Elements can be changed via scene graph at runtime.
 *
 * @param centerVertically also align vertically to middle
 * @param
 * @param invertedYOrder reverse order of elements - last child will be the top most
 * @author Jan Tiemann
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

    init {
        elements.forEach { this.addChild(it) }
        pack()
        postUpdate += {pack()}
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
                    // TODO Textboard.updateSize doesn't properly update its width yet, so horizontal centering remains broken
                    val x = if (centerHorizontally) -it.width / 2f else 0f
                    position = Vector3f(x, indexHeight, 0f)
                    needsUpdate = true
                }
                indexHeight += it.height + margin
            }

            spatial {
                position.y = if (centerVertically) (indexHeight - margin) * -0.5f else 0f
                needsUpdate = true
            }

            width = uiChildren.maxOf { it.width }
        }
    }
}
