package graphics.scenery

import cleargl.GLMatrix
import cleargl.GLVector
import java.lang.IllegalArgumentException

/**
 * This class generates a smooth curve within the convex hull of the control points. Note that the spline
 * does not necessarily pass through the control points. If you need a curve, which actually contains the
 * control points, use the Catmull Rom Spline instead. However, if this is not required and you prefer a
 * smoother curve over the exact one, this is the spline to draw.
 * The control points are represented by a List of Vectors ([controlPoints]) and the number of points the
 * curve shall contain is [n], which is the hundred by default.
 * The following code calculates firstly equidistant parameters for the curve calculation (the t in C(t)). Then we
 * calculate the curve segment by segment with four points each. The maths of uniform B-Splines are described, for
 * example, in: "Computer Graphics: Principles and Practice, Third Edition" by James D. Foley et al.
 *
 * @author Justin Bürger <burger@mpi-cbg.de>
 */
class UniformBSpline(override val controlPoints: ArrayList<GLVector>, override val n: Int = 100): Spline(controlPoints, n) {

    /**
     * This is a list of the equidistant parameters at which the curve is calculated.
     */
    private val tList = ArrayList<GLVector>(n+1)

    /**
     * Computes the actual tList.
     */
    private fun calculateT() {
        for(i in 0..n) {
            val t = i/n.toFloat()
            val tVector = GLVector((1-t)*(1-t)*(1-t)/6,
                    (3*t*t*t - 6*t*t +4)/6,
                    (-3*t*t*t + 3*t*t + 3*t +1)/6,
                    t*t*t/6)
            tList.add(tVector)
        }
    }

    /**
     * Returns the [n]*(numberOf([controlPoints])-3)+1 curve points the B-Spline has.
     */
    override fun splinePoints(): ArrayList<GLVector> {
        //checks if the controlpoints contain only a list of the same vectors
        if(controlPoints.toSet().size == 1) {
            throw IllegalArgumentException("The UniformBSpline got a list of the same points.")
        }
        return if(controlPoints.size < 4) {
            println("The list of controlPoints provided for the Uniform BSpline is empty or has less than four points.")
            controlPoints
        }
        else {
            calculateT()
            val curvePoints = ArrayList<GLVector>((controlPoints.size - 3) * n + 1)
            controlPoints.dropLast(3).forEachIndexed { index, _ ->
                val spline = partialSpline(controlPoints[index], controlPoints[index + 1],
                    controlPoints[index + 2], controlPoints[index + 3])
                curvePoints.addAll(spline)
            }
            return curvePoints
        }
    }

    /**
     * Calculates the partial Spline of four consecutive points.
     */
    private fun partialSpline(p1: GLVector, p2: GLVector, p3: GLVector, p4: GLVector): ArrayList<GLVector> {
        val pointMatrix = GLMatrix(floatArrayOf(
                p1.x(), p2.x(), p3.x(), p4.x(),
                p1.y(), p2.y(), p3.y(), p4.y(),
                p1.z(), p2.z(), p3.z(), p4.z(),
                0f, 0f, 0f, 0f)).transpose()
        val partialSpline = ArrayList<GLVector>(n)
        tList.forEach {
            partialSpline.add(pointMatrix.mult(it).xyz())
        }
        return(partialSpline)
    }
}
