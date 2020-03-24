package graphics.scenery

import cleargl.GLVector
import kotlin.math.pow

/**
 * This class offers the logic for creating a CatmuLL Rom Spline. This is essentially
 * a spline going through control points. For more information see:
 * https://en.wikipedia.org/wiki/Centripetal_Catmull–Rom_spline
 * [controlPoints] the list of control points
 * [alpha] determines the kind of Catmull Rom Spline, set in range of 0..1, the
 * resulting curve for alpha = 0 is a standart Catmull Rom Spline, for alpha = 1 we get
 * a chordal Catmull Rom Spline.
 */
class CatmullRomSpline(val controlPoints: List<GLVector>, val alpha: Float = 0.5f) {

    /**
     * Calculates the parameter t; t is an intermediate product for the calculation of the spline
     */
    private fun getT(ti: Float, Pi: GLVector, Pj: GLVector): Float {
        val exp: Float = (alpha*0.5).toFloat()
        return(((Pj.x()-Pi.x()).pow(2) + (Pj.y()-Pi.y()).pow(2)
            + (Pj.z()-Pi.z()).pow(2)).pow(exp) + ti)
    }

    /*
     * this function returns the spline points between two points. Please note you need four points
     * to have a smooth curve.
     * [n] is the number of points between the segments
     */
    private fun CatmulRomSpline(p0: GLVector, p1: GLVector, p2: GLVector, p3: GLVector, n: Int = 100): List<GLVector> {

        val curvePoints = ArrayList<GLVector>(n+1)

        val t0 = 0.toFloat()
        val t1 = getT(t0, p0, p1)
        val t2 = getT(t1, p1, p2)
        val t3 = getT(t2, p2, p3)

        var t = t1
        while(t<t2) {
            //The t's must not be equal, otherwise we divide by zero
            if(t1 != t0 && t2 != t1 && t2 != t0 && t3 != t1 && t3 != t2) {
                val a1 = p0.times((t1 - t) / (t1 - t0)) + p1.times((t - t0) / (t1 - t0))
                val a2 = p1.times((t2 - t) / (t2 - t1)) + p2.times((t - t1) / (t2 - t1))
                val a3 = p2.times((t3 - t) / (t3 - t2)) + p3.times((t - t2) / (t3 - t2))

                val b1 = a1.times((t2 - t) / (t2 - t0)) + a2.times((t - t0) / (t2 - t0))
                val b2 = a2.times((t3 - t) / (t3 - t1)) + a3.times((t - t1) / (t3 - t1))

                val c = b1.times((t2 - t) / (t2 - t1)) + b2.times((t - t1) / (t2 - t1))
                curvePoints.add(c)

                t += ((t2 - t1) / n)
            }
            else {
                throw IllegalArgumentException("The intermediate products of the calculations must not be equal!" +
                    "Otherwise we devide by zero.")
            }
        }

        return curvePoints
    }

    /**
     * Returns the actual curve with all the points.
     * [n] number of points the curve has
     */
    fun catMullRomChain(n: Int = 100): ArrayList<GLVector> {
        val chainPoints = ArrayList<GLVector>(controlPoints.size*(n+1))
        controlPoints.dropLast(3).forEachIndexed {  index, _ ->
            val c = CatmulRomSpline(controlPoints[index], controlPoints[index+1],
                controlPoints[index+2], controlPoints[index+3], n)
            chainPoints.addAll(c)
        }
        return chainPoints
    }

}
