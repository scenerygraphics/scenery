package graphics.scenery.flythroughs

import graphics.scenery.Camera
import graphics.scenery.geometry.FrenetFrame
import graphics.scenery.geometry.Curve
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour

class CurveCoaster(curve: Curve, val cam: () -> Camera?, override val listOfCameraFrames: List<FrenetFrame> = curve.frenetFrames,
                   override val offsetList: List<List<Vector3f>> = curve.baseShapes): Rollercoaster(cam), ClickBehaviour
{
    override fun click(x: Int, y: Int) {
        flyToNextPoint()
    }
}
