package graphics.scenery.geometry.curve

import graphics.scenery.Mesh
import graphics.scenery.geometry.Spline
import org.joml.Vector3f

/**
 * Constructs a geometry along the calculates points of a Spline.
 * The number n corresponds to the number of segments you wish to have between your control points.
 * The spline and the baseShape lambda must both have the same number of elements, otherwise, the curve is no
 * longer well-defined. Concerning the individual baseShapes, no lines must cross for the body of the curve to
 * be visualized flawlessly. Furthermore, all baseShapes ought to be convex.
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 * @param [baseShape] a lambda which returns all the baseShapes along the curve
 * @param [spline] the spline along which the geometry will be rendered
 */
class DefaultCurve(override val spline: Spline,
                   override val baseShapes: () -> List<List<Vector3f>>,
                   override val firstPerpendicularVector3f: Vector3f= Vector3f(0f, 0f, 0f),
): FrenetOrientedCurve, Mesh("DefaultCurve") {

    override val frenetFrames: () -> List<FrenetFrame> =
        { FrenetFrameCalculator.computeFrenetFrames(spline, firstPerpendicularVector3f) }
    init {
        val pointsPerSection = spline.pointsPerSection()
        val transformedBaseShapes = transformedBaseShapes(baseShapes.invoke(), frenetFrames.invoke())
        val subShapes = transformedBaseShapes.windowed(pointsPerSection + 1, pointsPerSection + 1, true)
        subShapes.forEachIndexed { index, list ->
            //fill gaps
            val arrayList = list as ArrayList
            if (index != subShapes.size - 1) {
                arrayList.add(subShapes[index + 1][0])
            }
            val cover = when (index) {
                0 -> {
                    CurveCover.Top
                }

                subShapes.lastIndex -> {
                    CurveCover.Bottom
                }

                else -> {
                    CurveCover.None
                }
            }
            val trianglesAndNormals = CurveGeometryCalculation.calculateTriangles(arrayList, cover = cover)
            val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
            this.addChild(partialCurve)
        }
    }
}
