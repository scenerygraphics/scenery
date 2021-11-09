package graphics.scenery.flythroughs

import graphics.scenery.Camera
import graphics.scenery.geometry.FrenetFrame
import graphics.scenery.geometry.FrenetFramesCalc
import graphics.scenery.geometry.Curve
import graphics.scenery.geometry.UniformBSpline
import graphics.scenery.proteins.Axis
import graphics.scenery.proteins.Helix
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.RibbonDiagram
import graphics.scenery.utils.LazyLogger
import org.joml.*
import org.scijava.ui.behaviour.ClickBehaviour
import org.slf4j.Logger


class ProteinRollercoasterSimple(ribbonDiagram: RibbonDiagram, override val cam: () -> Camera?)
    : ProteinRollercoaster(ribbonDiagram, cam), ClickBehaviour {
    override fun click(x: Int, y: Int) {
        flyToNextPoint()
    }
}
