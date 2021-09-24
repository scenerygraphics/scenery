package graphics.scenery.flythroughs

import graphics.scenery.Camera
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.RibbonDiagram
import org.scijava.ui.behaviour.ClickBehaviour

class ReverseProteinCoaster(cam: () -> Camera?, protein: RibbonDiagram): ClickBehaviour {
    val frames = protein.children
    override fun click(x: Int, y: Int) {
        TODO("Not yet implemented")
    }
}
