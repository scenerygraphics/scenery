package graphics.scenery.geometry

import graphics.scenery.utils.extensions.xyz
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
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
class UniformBSpline(protected val controlPoints: ArrayList<Vector3f>, val n: Int = 100): Spline {

    /**
     * This is a list of the equidistant parameters at which the curve is calculated.
     */
    private val tList = ArrayList<Vector4f>(n+1)
    /**
     * Computes the actual tList.
     */
    init {
        for(i in 0..n) {
            val t = i/n.toFloat()
            val tVector = Vector4f((1-t)*(1-t)*(1-t)/6,
                    (3*t*t*t - 6*t*t +4)/6,
                    (-3*t*t*t + 3*t*t + 3*t +1)/6,
                    t*t*t/6)
            tList.add(tVector)
        }
    }

    /**
     * Returns the [n]*(numberOf([controlPoints])-3)+1 curve points the B-Spline has.
     */
    override fun splinePoints(): ArrayList<Vector3f> {
        //checks if the controlpoints contain only a list of the same vectors
        if(controlPoints.toSet().size == 1) {
            throw IllegalArgumentException("The UniformBSpline got a list of the same points.")
        }
        return if(controlPoints.size < 4) {
            println("The list of controlPoints provided for the Uniform BSpline is empty or has less than four points.")
            ArrayList()
        }
        else {
            val curvePoints = ArrayList<Vector3f>((controlPoints.size - 3) * (n + 1))
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
    private fun partialSpline(p1: Vector3f, p2: Vector3f, p3: Vector3f, p4: Vector3f): ArrayList<Vector3f> {
        val pointMatrix = Matrix4f(
                p1.x(), p1.y(), p1.z(), 0f,
                p2.x(), p2.y(), p2.z(), 0f,
                p3.x(), p3.y(), p3.z(), 0f,
                p4.x(), p4.y(), p4.z(), 0f)
        val partialSpline = ArrayList<Vector3f>(n)
        tList.forEach {
            val vec = Vector4f(it)
            val transformVec = pointMatrix.transform(vec)
            partialSpline.add(transformVec.xyz())
        }
        return(partialSpline)
    }

    override fun controlPoints(): List<Vector3f> {
        return  controlPoints
    }

    override fun verticesCountPerSection(): Int {
        return n
    }

}
