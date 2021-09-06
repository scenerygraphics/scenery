package graphics.scenery.controls.behaviours

import graphics.scenery.Scene
import graphics.scenery.proteins.RibbonDiagram
import org.scijava.ui.behaviour.ClickBehaviour

class RibbonDiagramClickByClick(private val name: String, val scene: Scene): ClickBehaviour {
    private var i = 0

    override fun click(x: Int, y: Int) {
        if(scene.children.filter { it.name == name }[0] is RibbonDiagram) {
            if (i <= scene.children.filter { it.name == name }[0].children.flatMap { subProtein -> subProtein.children }
                    .flatMap { curve -> curve.children }.lastIndex) {
                scene.children.filter { it.name == name }[0].children.flatMap { subProtein -> subProtein.children }
                    .flatMap { curve -> curve.children }[i].visible = true
            }
            i+=1
        }
    }
}
