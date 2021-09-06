package graphics.scenery.controls.behaviours

import graphics.scenery.Scene
import graphics.scenery.proteins.RibbonDiagram
import org.scijava.ui.behaviour.ClickBehaviour

class RibbonDiagramClickByClick(private val ribbon: RibbonDiagram, val scene: Scene): ClickBehaviour {
    private var i = 0
    init {
        ribbon.visible = false
        scene.addChild(ribbon)
    }

    override fun click(x: Int, y: Int) {
        ribbon.children.flatMap { subProtein -> subProtein.children }.flatMap { curve -> curve.children }[i].visible = true
        i+=1
    }
}
