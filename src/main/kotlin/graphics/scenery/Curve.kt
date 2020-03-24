package graphics.scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.FloatUtil.makeRotationAxis
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.acos

/**
 * Constructs a geometry along the calculates points of a Spline (in this case a Catmull Rom Spline).
 * This class inherits from Node and HasGeometry
 * The number n corresponds to the number of segments you wish to have between you control points.
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class Curve(curve: CatmullRomSpline, baseShape: () -> ArrayList<GLVector>): Node("CurveGeometry"), HasGeometry {
    override val vertexSize = 3
    override val texcoordSize = 2
    override var geometryType = GeometryType.TRIANGLES

    override var vertices: FloatBuffer = BufferUtils.allocateFloat(0)
    override var normals: FloatBuffer = BufferUtils.allocateFloat(0)
    override var texcoords: FloatBuffer = BufferUtils.allocateFloat(0)
    override var indices: IntBuffer = BufferUtils.allocateInt(0)

    private val chain = curve.catMullRomChain()

    /**
     * This function renders the spline.
     * [baseShape] It takes a lambda as a parameter, which is the shape of the
     * curve.
     * If you choose, for example, to have a square as a base shape, your spline will look like
     * a banister. Please not that the base shape needs an equal number of points in each segments but it
     * can very well vary in thickness.
     */
    init {
        val bases = ArrayList<GLMatrix>()
        computeFrenetFrames(chain).forEach { (t, n, b, tr) ->
            if(n != null && b != null) {
                val inverseMatrix = GLMatrix(floatArrayOf(
                    n.x(), b.x(), t.x(), 0f,
                    n.y(), b.y(), t.y(), 0f,
                    n.z(), b.z(), t.z(), 0f,
                    0f, 0f, 0f, 1f)).inverse
                val nn = GLVector(inverseMatrix[0, 0], inverseMatrix[1, 0], inverseMatrix[2, 0]).normalized
                val nb = GLVector(inverseMatrix[0, 1],inverseMatrix[1, 1], inverseMatrix[2, 1]).normalized
                val nt = GLVector(inverseMatrix[0, 2], inverseMatrix[1, 2], inverseMatrix[2, 2]).normalized
                val matrix = GLMatrix(floatArrayOf(
                    nn.x(), nb.x(), nt.x(), 0f,
                    nn.y(), nb.y(), nt.y(), 0f,
                    nn.z(), nb.z(), nt.z(), 0f,
                    tr.x(), tr.y(), tr.z(), 1f))
                bases.add(matrix)
            }
            else {
                throw IllegalStateException("Tangent and normal must not be null!")
            }
        }

        val curveGeometry = bases.map { basis ->
            baseShape.invoke().map { v ->
                val vector4D = GLVector(v.x(), v.y(), v.z(), 1f)
                val vector = basis.mult(vector4D)
                vector.xyz()
            }
        }

        val verticesVectors = calculateTriangles(curveGeometry)

        vertices = BufferUtils.allocateFloat(verticesVectors.size*3)
        verticesVectors.forEach{
            vertices.put(it.xyz().toFloatArray())
        }
        vertices.flip()
        texcoords = BufferUtils.allocateFloat(verticesVectors.size*2)
        recalculateNormals()
    }

    /**
     * This function calculates the tangent at a given index in the catmull rom curve.
     * [i] index of the curve (not the geometry!)
     */
    private fun getTangent(i: Int): GLVector {
        val s = chain.size
        return when(i) {
            0 -> ((chain[i+1] - chain[i]).normalized)
            (s-2) -> ((chain[i+1] - chain[i]).normalized)
            (s-1) -> ((chain[i] - chain[i-1]).normalized)
            else -> ((chain[i+1] - chain[i-1]).normalized)
        }
    }

    /**
     * Data class to store Frenet frames (wandering coordinate systems), consisting of [tangent], [normal], [bitangent]
     */
    data class FrenetFrame(val tangent: GLVector, var normal: GLVector?, var bitangent: GLVector?, val translation: GLVector)
    /**
     * This function returns the frenet frames along the curve. This is essentially a new
     * coordinate system which represents the form of the curve. For details concerning the
     * calculation see: http://www.cs.indiana.edu/pub/techreports/TR425.pdf
     */
    fun computeFrenetFrames(curv: ArrayList<GLVector>): List<FrenetFrame> {

        val frenetFrameList = ArrayList<FrenetFrame>(curv.size)

        if(curv.isEmpty()) {
            return frenetFrameList
        }

        //adds all the tangent vectors
        curv.forEachIndexed { index, _ ->
            val frenetFrame = FrenetFrame(getTangent(index), null, null, curv[index])
            frenetFrameList.add(frenetFrame)
        }

        //initial normal vector perpendicular to first tangent vector
        val vec = if(frenetFrameList[0].tangent.x() >= 0.9f || frenetFrameList[0].tangent.z() >= 0.9f) {
            GLVector(0f, 1f, 0f)
        }
        else {
            GLVector(1f, 0f, 0f)
        }

        val normal = frenetFrameList[0].tangent.cross(vec).normalized

        frenetFrameList[0].normal = normal
        frenetFrameList[0].bitangent = frenetFrameList[0].tangent.cross(normal).normalized

        frenetFrameList.windowed(2,1).forEach { (firstFrame, secondFrame) ->
            val b = firstFrame.tangent.cross(secondFrame.tangent).normalized
            //if there is no substantial difference between two tangent vectors, the frenet frame need not to change
            if (b.length2() < 0.0001f) {
                secondFrame.normal = firstFrame.normal
                secondFrame.bitangent = firstFrame.bitangent
            } else {
                val firstNormal = firstFrame.normal

                val theta = acos(firstFrame.tangent.times(secondFrame.tangent))
                val emptyMatrix = GLMatrix()
                if (normal != null && firstNormal != null) {
                    val rotationMatrix = GLMatrix(makeRotationAxis(
                        emptyMatrix.floatArray,
                        0,
                        theta,
                        firstNormal.x(),
                        firstNormal.y(),
                        firstNormal.z(),
                        firstNormal.toFloatArray()
                    ))
                    val normal4D = GLVector(firstNormal.x(), firstNormal.y(), firstNormal.z(), 1f)
                    secondFrame.normal = rotationMatrix.mult(normal4D).xyz().normalized
                }
                else {
                    throw IllegalStateException("Normals must not be null!")
                }
                secondFrame.bitangent = secondFrame.tangent.cross(secondFrame.normal).normalized
            }
        }
        return frenetFrameList.filterNot { it.bitangent!!.toFloatArray().all { value -> value.isNaN() } &&
                                            it.normal!!.toFloatArray().all{ value -> value.isNaN()}}
    }

    /**
     * This function calculates the triangles for the the rendering. It takes as a parameter
     * the [curveGeometry] List which contains all the baseShapes transformed and translated
     * along the curve.
     */
    private fun calculateTriangles(curveGeometry: List<List<GLVector>>): ArrayList<GLVector> {
        val verticesVectors = ArrayList<GLVector>()
        if(curveGeometry.isEmpty()) {
            return verticesVectors
        }
        //if none of the lists in the curveGeometry differ in size, distinctBy leaves only one element
        if(curveGeometry.distinctBy{ it.size }.size == 1) {
            curveGeometry.dropLast(1).forEachIndexed { shapeIndex, shape ->
                shape.dropLast(1).forEachIndexed { vertexIndex, _ ->

                    verticesVectors.add(curveGeometry[shapeIndex][vertexIndex])
                    verticesVectors.add(curveGeometry[shapeIndex][vertexIndex + 1])
                    verticesVectors.add(curveGeometry[shapeIndex + 1][vertexIndex])

                    verticesVectors.add(curveGeometry[shapeIndex][vertexIndex + 1])
                    verticesVectors.add(curveGeometry[shapeIndex + 1][vertexIndex + 1])
                    verticesVectors.add(curveGeometry[shapeIndex + 1][vertexIndex])
                }
                verticesVectors.add(curveGeometry[shapeIndex][0])
                verticesVectors.add(curveGeometry[shapeIndex + 1][0])
                verticesVectors.add(curveGeometry[shapeIndex + 1][shape.lastIndex])

                verticesVectors.add(curveGeometry[shapeIndex + 1][shape.lastIndex])
                verticesVectors.add(curveGeometry[shapeIndex][shape.lastIndex])
                verticesVectors.add(curveGeometry[shapeIndex][0])
            }
        }
        else {
            throw IllegalArgumentException("The baseShapes must not differ in size!")
        }
        return verticesVectors
    }

    /**
     * Getter for the curve.
     */
    fun getCurve(): ArrayList<GLVector> {
        return chain
    }
}
