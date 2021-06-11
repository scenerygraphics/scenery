package graphics.scenery

import graphics.scenery.utils.extensions.toFloatArray
import org.joml.*
import kotlin.math.acos

class FrenetFramesCalc(val spline: Spline, private val firstPerpendicularVector: Vector3f = Vector3f()) {

    private val chain = spline.splinePoints()

    /**
     * This function returns the frenet frames along the curve. This is essentially a new
     * coordinate system which represents the form of the curve. For details concerning the
     * calculation see: http://www.cs.indiana.edu/pub/techreports/TR425.pdf
     */
    fun computeFrenetFrames(): List<FrenetFrame> {

        val frenetFrameList = ArrayList<FrenetFrame>(chain.size)

        if(chain.isEmpty()) {
            return frenetFrameList
        }

        //adds all the tangent vectors
        chain.forEachIndexed { index, _ ->
            val frenetFrame = FrenetFrame(getTangent(index), Vector3f(), Vector3f(), chain[index])
            frenetFrameList.add(frenetFrame)
        }
        var min = Float.MIN_VALUE
        val vec = Vector3f(0f, 0f, 0f)
        vec.set(firstPerpendicularVector)
        if(vec == Vector3f(0f, 0f, 0f)) {
            val normal = Vector3f()
            if (frenetFrameList[0].tangent.x() <= min) {
                min = frenetFrameList[0].tangent.x()
                normal.set(1f, 0f, 0f)
            }
            if (frenetFrameList[0].tangent.y() <= min) {
                min = frenetFrameList[0].tangent.y()
                normal.set(0f, 1f, 0f)
            }
            if (frenetFrameList[0].tangent.z() <= min) {
                normal.set(0f, 0f, 1f)
            } else {
                normal.set(1f, 0f, 0f).normalize()
            }
            frenetFrameList[0].tangent.cross(normal, vec).normalize()
        }
        else { vec.normalize() }
        frenetFrameList[0].tangent.cross(vec, frenetFrameList[0].normal).normalize()
        frenetFrameList[0].tangent.cross(frenetFrameList[0].normal, frenetFrameList[0].binormal).normalize()

        frenetFrameList.windowed(2,1).forEach { (firstFrame, secondFrame) ->
            val b = Vector3f(firstFrame.tangent).cross(secondFrame.tangent)
            secondFrame.normal = firstFrame.normal.normalize()
            //if there is no substantial difference between two tangent vectors, the frenet frame need not to change
            if (b.length() > 0.00001f) {
                val firstNormal = firstFrame.normal
                b.normalize()
                val theta = acos(firstFrame.tangent.dot(secondFrame.tangent).coerceIn(-1f, 1f))
                val q = Quaternionf(AxisAngle4f(theta, b)).normalize()
                secondFrame.normal = q.transform(Vector3f(firstNormal)).normalize()
            }
            secondFrame.tangent.cross(secondFrame.normal, secondFrame.binormal).normalize()
        }
        return frenetFrameList.filterNot { it.binormal.toFloatArray().all { value -> value.isNaN() } &&
            it.normal.toFloatArray().all{ value -> value.isNaN()}}
    }

    /**
     * This function calculates the tangent at a given index.
     * [i] index of the curve (not the geometry!)
     */
    private fun getTangent(i: Int): Vector3f {
        if(chain.size >= 3) {
            val tangent = Vector3f()
            when (i) {
                0 -> { ((chain[1].sub(chain[0], tangent)).normalize()) }
                1 -> { ((chain[2].sub(chain[0], tangent)).normalize()) }
                chain.lastIndex - 1 -> { ((chain[i + 1].sub(chain[i - 1], tangent)).normalize()) }
                chain.lastIndex -> { ((chain[i].sub(chain[i - 1], tangent)).normalize()) }
                else -> {
                    chain[i+1].sub(chain[i-1], tangent).normalize()
                }
            }
            return tangent
        }
        else {
            throw Exception("The spline deosn't provide enough points")
        }
    }

    /**
     * Calculates the bases
     */
    fun calcBases(frenetFrameList: List<FrenetFrame>): List<Matrix4f> {
        val bases = frenetFrameList.map { (t, n, b, tr) ->
            val inverseMatrix = Matrix3f(b.x(), n.x(), t.x(),
                b.y(), n.y(), t.y(),
                b.z(), n.z(), t.z()).invert()
            val nb = Vector3f()
            inverseMatrix.getColumn(0, nb).normalize()
            val nn = Vector3f()
            inverseMatrix.getColumn(1, nn).normalize()
            val nt = Vector3f()
            inverseMatrix.getColumn(2, nt).normalize()
            Matrix4f(
                nb.x(), nn.x(), nt.x(), 0f,
                nb.y(), nn.y(), nt.y(), 0f,
                nb.z(), nn.z(), nt.z(), 0f,
                tr.x(), tr.y(), tr.z(), 1f)
        }
        return bases
    }
}
