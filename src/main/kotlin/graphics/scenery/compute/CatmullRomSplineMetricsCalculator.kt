package graphics.scenery.compute

import graphics.scenery.geometry.CatmullRomSpline
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Vector3f
import org.joml.minus
import kotlin.math.pow

class CatmullRomSplineMetricsCalculator(spline: CatmullRomSpline, val alpha: Float): SplineMetricsCalculator(spline) {

    /**
     * Analytical solution for curvature of Catmull-Rom spline.
     *
     * @param padding if true, curvature list is padded with zeros to match the number of vertices in the spline.
     * @return List of curvature values for each point of the spline.
     * */
    override fun curvature(padding: Boolean): List<Float> {
        val points = spline.controlPoints()
        val curvatureList = ArrayList<Float>()
        val n = spline.verticesCountPerSection()

        for (i in 0 until points.lastIndex-3) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val p2 = points[i + 2]
            val p3 = points[i + 3]

            val t0 = 0.toFloat()
            val t1 = getT(t0, p0, p1)
            val t2 = getT(t1, p1, p2)
            val t3 = getT(t2, p2, p3)

            var t = t1
            while (t < t2) {
                //The t's must not be equal, otherwise we divide by zero
                if (t1 != t0 && t2 != t1 && t2 != t0 && t3 != t1 && t3 != t2) {
                    val a1 = p0.times((t1 - t) / (t1 - t0)) + p1.times((t - t0) / (t1 - t0))
                    val a1_deriv = 1/(t1 - t0) * (p1.minus(p0))

                    val a2 = p1.times((t2 - t) / (t2 - t1)) + p2.times((t - t1) / (t2 - t1))
                    val a2_deriv = 1/(t2 - t1) * (p2.minus(p1))

                    val a3 = p2.times((t3 - t) / (t3 - t2)) + p3.times((t - t2) / (t3 - t2))
                    val a3_deriv = 1/(t3 - t2) * (p3.minus(p2))

                    val b1 = a1.times((t2 - t) / (t2 - t0)) + a2.times((t - t0) / (t2 - t0))
                    val b1_deriv = 1/(t2-t0)*(a2-a1)+
                        (t2-t)/(t2-t0)*(a1_deriv) +
                        (t-t0)/(t2-t0)*(a2_deriv)
                    val b1_scnd_deriv = 2/(t2-t0)*(a2_deriv-a1_deriv)

                    val b2 = a2.times((t3 - t) / (t3 - t1)) + a3.times((t - t1) / (t3 - t1))
                    val b2_deriv = 1/(t3-t1)*(a3-a2)+
                        (t3-t)/(t3-t1)*(a2_deriv) +
                        (t-t1)/(t3-t1)*(a3_deriv)
                    val b2_scnd_deriv = 2/(t3-t1)*(a3_deriv-a2_deriv)

                    val c = b1.times((t2 - t) / (t2 - t1)) + b2.times((t - t1) / (t2 - t1))
                    val c_deriv = 1/(t2-t1)*(b2-b1)+
                        (t2-t)/(t2-t1)*(b1_deriv) +
                        (t-t1)/(t2-t1)*(b2_deriv)
                    val c_scnd_deriv = 2/(t2-t1)*(b2_deriv-b1_deriv)+
                        (t2-t)/(t2-t1)*b1_scnd_deriv+
                        (t-t1)/(t2-t1)*b2_scnd_deriv

                    val curvature = Vector3f(c_deriv).cross(c_scnd_deriv).length() / c_deriv.length().pow(3)
                    curvatureList.add(curvature)
                    t += ((t2 - t1) / n)
                } else {
                    throw IllegalArgumentException(
                        "The intermediate products of the calculations must not be equal!" +
                            "Otherwise we devide by zero."
                    )
                }
            }
        }
        return curvatureList
    }


    // duplicate of the getT function from CatmullRomSpline
    private fun getT(ti: Float, Pi: Vector3f, Pj: Vector3f): Float {
        val exp: Float = (this.alpha*0.5).toFloat()
        return(((Pj.x()-Pi.x()).pow(2) + (Pj.y()-Pi.y()).pow(2)
            + (Pj.z()-Pi.z()).pow(2)).pow(exp) + ti)
    }

}
