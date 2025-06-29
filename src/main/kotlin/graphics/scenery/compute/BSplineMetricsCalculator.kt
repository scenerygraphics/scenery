package graphics.scenery.compute

import graphics.scenery.geometry.UniformBSpline
import graphics.scenery.utils.extensions.xyz
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.pow

class BSplineMetricsCalculator(spline: UniformBSpline, val parameters: Matrix4f, val tList: ArrayList<Vector4f>): SplineMetricsCalculator(spline) {

    /**
     * Analytical solution for curvature across all segments of a B-Spline expressed in parameter-based notation.
     *
     * @param padding boolean indicating whether the curvature list should be padded with zeros to match the size of the point list.
     * @return ArrayList of curvature values for each (second order) differentiable point of the B-Spline.
     **/
    override fun curvature(padding: Boolean): ArrayList<Float> {
        // TODO: check, if curvatureList is same size as point list -> should be the case, since BSpline is differientiable at every point -> padding becomes useless
        val controlPoints = spline.controlPoints()
        val curvatureList = ArrayList<Float>((controlPoints.size-3)*tList.size)
        for (cpIndex in 0 until controlPoints.size-3){
            val pointMatrix = createPointMatrix(controlPoints, cpIndex)
            val segmentCurvatures = computeSegmentCurvatures(cpIndex, pointMatrix)
            curvatureList.addAll(segmentCurvatures)
        }
        return curvatureList
    }

    /**
     * Analytical solution for curvature across one segment descibed by 4 control points and parameter t.
     *
     * @param cpIndex Index of the segment to compute curvature for.
     * @param pointMatrix Matrix representing the control points of the segment.
     * @return ArrayList of curvature values for the segment.
     * */
    private fun computeSegmentCurvatures(cpIndex: Int, pointMatrix: Matrix4f): ArrayList<Float> {
        val curvatures = ArrayList<Float>()
        // TODO: reverse local t list? Because there is reversal in original spline calculation
        tList.forEachIndexed { tIndex, it ->
            if (tIndex != tList.lastIndex || cpIndex == 0) {
                val tval = it.y

                // Derivative vectors
                val tvecDeriv = Vector4f(0f, 1f, 2 * tval, 3 * tval * tval)
                val tvecScndDeriv = Vector4f(0f, 0f, 2f, 6 * tval)

                val firstDeriv = pointMatrix.transform(Vector4f(parameters.transform(tvecDeriv).mul(1 / 6f))).xyz()
                val secondDeriv =
                    pointMatrix.transform(Vector4f(parameters.transform(tvecScndDeriv).mul(1 / 6f))).xyz()

                val denominator = Vector3f(firstDeriv).length().pow(3)  // tangent length to the power of 3
                val nominator = Vector3f(firstDeriv).cross(secondDeriv).length()

                if (denominator == 0f) {
                    println("Warning: Zero first derivative at =$tval in segment $cpIndex")
                    curvatures.add(0f)
                } else {
                    curvatures.add(nominator / denominator)
                }
            }
        }
        return curvatures
    }

    /**
     * Creates a 4x4 matrix from the control points of a B-Spline segment.
     *
     * @param controlPoints List of control points for the B-Spline.
     * @param startIndex Index of the first control point in the segment.
     * @return A 4x4 matrix representing the control points of the segment.
     */
    private fun createPointMatrix(controlPoints: List<Vector3f>, startIndex: Int): Matrix4f {
        val p1 = controlPoints[startIndex]
        val p2 = controlPoints[startIndex + 1]
        val p3 = controlPoints[startIndex + 2]
        val p4 = controlPoints[startIndex + 3]
        return Matrix4f(
            p1.x, p1.y, p1.z, 0f,
            p2.x, p2.y, p2.z, 0f,
            p3.x, p3.y, p3.z, 0f,
            p4.x, p4.y, p4.z, 0f
        )
    }


}
