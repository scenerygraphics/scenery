package graphics.scenery

import cleargl.GLVector
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Constructs a Icosphere with the given [radius] and number of [subdivisions].
 *
 * @author Ulrik Günther <hello@ulrik.is>, based on code by Andreas Kahler, http://blog.andreaskahler.com/2009/06/creating-icosphere-mesh-in-code.html
 * @param[radius] The radius of the sphere
 * @param[subdivisions] Number of subdivisions of the base icosahedron
 */
open class Icosphere(val radius: Float, val subdivisions: Int) : Node("Icosphere"), HasGeometry {
    override val vertexSize = 3
    override val texcoordSize = 2
    override var geometryType = GeometryType.TRIANGLES

    override var vertices: FloatBuffer = BufferUtils.allocateFloat(0)
    override var normals: FloatBuffer = BufferUtils.allocateFloat(0)
    override var texcoords: FloatBuffer = BufferUtils.allocateFloat(0)
    override var indices: IntBuffer = BufferUtils.allocateInt(0)

    fun MutableList<GLVector>.addVertex(vararg v: Float) {
        this.add(GLVector(*v))
    }

    fun MutableList<Triple<Int, Int, Int>>.addFace(i: Int, j: Int, k: Int) {
        this.add(kotlin.Triple(i, j, k))
    }

    protected fun createBaseVertices(vertices: MutableList<GLVector>, indices: MutableList<Triple<Int, Int, Int>>) {
        val s = sqrt((5.0f - sqrt(5.0f)) / 10.0f)
        val t = sqrt((5.0f + sqrt(5.0f)) / 10.0f)

        vertices.addVertex(-s, t, 0.0f)
        vertices.addVertex(s, t, 0.0f)
        vertices.addVertex(-s, -t, 0.0f)
        vertices.addVertex(s, -t, 0.0f)

        vertices.addVertex(0.0f, -s, t)
        vertices.addVertex(0.0f, s, t)
        vertices.addVertex(0.0f, -s, -t)
        vertices.addVertex(0.0f, s, -t)

        vertices.addVertex(t, 0.0f, -s)
        vertices.addVertex(t, 0.0f, s)
        vertices.addVertex(-t, 0.0f, -s)
        vertices.addVertex(-t, 0.0f, s)

        indices.addFace(0, 11, 5)
        indices.addFace(0, 5, 1)
        indices.addFace(0, 1, 7)
        indices.addFace(0, 7, 10)
        indices.addFace(0, 10, 11)

        // 5 adjacent faces
        indices.addFace(1, 5, 9)
        indices.addFace(5, 11, 4)
        indices.addFace(11, 10, 2)
        indices.addFace(10, 7, 6)
        indices.addFace(7, 1, 8)

        // 5 faces around point 3
        indices.addFace(3, 9, 4)
        indices.addFace(3, 4, 2)
        indices.addFace(3, 2, 6)
        indices.addFace(3, 6, 8)
        indices.addFace(3, 8, 9)

        // 5 adjacent faces
        indices.addFace(4, 9, 5)
        indices.addFace(2, 4, 11)
        indices.addFace(6, 2, 10)
        indices.addFace(8, 6, 7)
        indices.addFace(9, 8, 1)
    }

    protected fun refineTriangles(recursionLevel: Int,
                                  vertices: MutableList<GLVector>,
                                  indices: MutableList<Triple<Int, Int, Int>>): List<Triple<Int, Int, Int>> {
        // refine triangles
        var faces = indices
        (0 until recursionLevel).forEach {
            val faces2 = ArrayList<Triple<Int, Int, Int>>(indices.size * 3)

            for (triangle in faces) {
                // replace triangle by 4 triangles
                val a = vertices.getMiddlePoint(triangle.first, triangle.second)
                val b = vertices.getMiddlePoint(triangle.second, triangle.third)
                val c = vertices.getMiddlePoint(triangle.third, triangle.first)

                faces2.addFace(triangle.first, a, c)
                faces2.addFace(triangle.second, b, a)
                faces2.addFace(triangle.third, c, b)
                faces2.addFace(a, b, c)
            }

            faces = faces2
        }

        return faces
    }

    protected fun MutableList<GLVector>.addVertex(v: GLVector): Int {
        this.add(v.normalized)
        return this.size - 1
    }

    private val middlePointIndexCache = HashMap<Long, Int>()
    protected fun MutableList<GLVector>.getMiddlePoint(p1: Int, p2: Int): Int {
        // first check if we have it already
        val firstIsSmaller = p1 < p2
        val smallerIndex = if(firstIsSmaller) { p1 } else { p2 }
        val greaterIndex = if(!firstIsSmaller) { p1 } else { p2 }
        val key: Long = (smallerIndex.toLong() shl 32) + greaterIndex.toLong()

        val ret = middlePointIndexCache[key]
        if (ret != null) {
            return ret
        }

        // not in cache, calculate it
        val point1 = this[p1]
        val point2 = this[p2]
        val middle = (point1 + point2) * 0.5f

        // add vertex makes sure point is on unit sphere
        val i = this.addVertex(middle)

        // store it, return index
        middlePointIndexCache.put(key, i)
        return i
    }

    init {
        val vertexBuffer = ArrayList<GLVector>()
        val indexBuffer = ArrayList<Triple<Int, Int, Int>>()

        createBaseVertices(vertexBuffer, indexBuffer)
        val faces = refineTriangles(subdivisions, vertexBuffer, indexBuffer)

        vertices = BufferUtils.allocateFloat(vertexBuffer.size * 3)
        normals = BufferUtils.allocateFloat(vertexBuffer.size * 3)
        texcoords = BufferUtils.allocateFloat(vertexBuffer.size * 2)
        indices = BufferUtils.allocateInt(faces.size * 3)

        faces.forEach { f ->
            indices.put(f.first)
            indices.put(f.second)
            indices.put(f.third)
        }

        vertexBuffer.forEach { v ->
            vertices.put((v * radius).toFloatArray())
            normals.put(v.toFloatArray())
            texcoords.put(0.5f - atan2(v.x(), v.z())/(2.0f * PI.toFloat()))
            texcoords.put(0.5f - asin(v.x()) / PI.toFloat())
        }

        vertices.flip()
        normals.flip()
        texcoords.flip()
        indices.flip()

        boundingBox = generateBoundingBox()
    }

}
