package graphics.scenery.ui

import graphics.scenery.RichNode

/**
 * Elements can be changed via scene graph at runtime.
 * @author Jan Tiemann
 */
open class Row(vararg elements: Gui3DElement, val margin: Float = 0.5f, var middleAlign: Boolean = true)
    : RichNode("UI Row"), Gui3DElement {

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
        val uiChildren = children.filterIsInstance(Gui3DElement::class.java)
        val currentWidth = uiChildren.sumOf { it.width.toDouble() }.toFloat() + (uiChildren.size-1)*margin
        if (currentWidth != width && uiChildren.isNotEmpty()){
            width = currentWidth
            var indexWidth = 0f
            uiChildren.forEach {
                it.spatial(){
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
        }
    }
}
