package graphics.scenery

import graphics.scenery.geometry.Curve
import org.joml.Vector3f

class CurveCoaster(curve: Curve, val cam: () -> Camera?, override val listOfCameraFrames: List<FrenetFrame> = curve.frenetFrames,
                   override val offsetList: List<List<Vector3f>> = curve.baseShapes): Rollercoaster(cam)
