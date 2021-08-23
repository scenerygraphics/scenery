package graphics.scenery.flythroughs
import graphics.scenery.Camera
import graphics.scenery.FrenetFrame
import graphics.scenery.FrenetFramesCalc
import graphics.scenery.geometry.Spline
import org.joml.Vector3f

class SplineCoaster(spline: Spline, val cam: () -> Camera?, override val listOfCameraFrames: List<FrenetFrame> =
    FrenetFramesCalc(spline).computeFrenetFrames(), override val offsetList: List<List<Vector3f>> = ArrayList<List<Vector3f>>()): Rollercoaster(cam)
