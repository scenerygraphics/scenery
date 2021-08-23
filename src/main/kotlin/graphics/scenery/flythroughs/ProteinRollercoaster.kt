package graphics.scenery.flythroughs

import graphics.scenery.Camera
import graphics.scenery.FrenetFrame
import graphics.scenery.FrenetFramesCalc
import graphics.scenery.geometry.Curve
import graphics.scenery.geometry.UniformBSpline
import graphics.scenery.proteins.Axis
import graphics.scenery.proteins.Helix
import graphics.scenery.proteins.RibbonDiagram
import graphics.scenery.utils.LazyLogger
import org.joml.*
import org.slf4j.Logger


class ProteinRollercoaster(ribbonDiagram: RibbonDiagram, val cam: () -> Camera?): Rollercoaster(cam) {
    private val subproteins = ribbonDiagram.children
    val controlpoints = ArrayList<Vector3f>(subproteins.size*10)
    override val offsetList: List<List<Vector3f>> = ArrayList<List<Vector3f>>()
    override val listOfCameraFrames = ArrayList<FrenetFrame>(subproteins.size*100)
    private val logger: Logger by LazyLogger()


    init {
        ribbonDiagram.children.forEach { subprotein ->
            subprotein.children.forEachIndexed{ index, subCurve ->
                if(subCurve is Curve) {
                    subCurve.frenetFrames.filterIndexed { index, _ -> index%10 == 1  }.forEachIndexed { _, frame ->
                        controlpoints.add(Vector3f(frame.translation.x, frame.translation.y + 0.7f, frame.translation.z))//offset to not fly through the baseShapes
                    }
                }
                /*
                 We don't want to ride along the helix because it could lead to motion sickness
                */
                else if (subCurve is Helix) {
                    controlpoints.addAll(helixSplinePoints(subCurve))
                }
            }
        }
        listOfCameraFrames.addAll(FrenetFramesCalc(UniformBSpline(controlpoints, 10)).computeFrenetFrames())
    }

    fun helixSplinePoints(helix: Helix): ArrayList<Vector3f> {
        val helixSpline = helix.spline
        val axisSplineList = ArrayList<Vector3f>(helixSpline.splinePoints().size)
        // midpoint of the helix since the axis calc does not give a good enough approximation
        val axisPos = Axis.getCentroid(helixSpline.splinePoints().drop(10).dropLast(10))
        val axisDir = helix.axis.direction
        helixSpline.splinePoints().drop(18).dropLast(18).filterIndexed{index, _ -> index%15 == 0}.forEach { splinePoint ->
            val newPoint = Vector3f()
            val t = (splinePoint.sub(axisPos, newPoint).dot(axisDir))/(axisDir.length()*axisDir.length())
            // this is the splinePoint mapped to the axis
            axisPos.add(axisDir.mul(t, newPoint), newPoint)
            axisSplineList.add(newPoint)
        }
        return axisSplineList
    }
}
