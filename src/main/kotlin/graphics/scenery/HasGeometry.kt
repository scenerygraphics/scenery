package graphics.scenery

import cleargl.GLVector
import gnu.trove.map.hash.THashMap
import gnu.trove.set.hash.TLinkedHashSet
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.SystemHelpers
import org.lwjgl.system.MemoryUtil.memAlloc
import java.io.BufferedInputStream
import java.io.File
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.file.Files
import java.util.*

/**
 * Interface for any [Node] that stores geometry in the form of vertices,
 * normals, texcoords, or indices.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface HasGeometry : Serializable {
    /** How many elements does a vertex store? */
    val vertexSize: Int
    /** How many elements does a texture coordinate store? */
    val texcoordSize: Int
    /** The [GeometryType] of the [Node] */
    var geometryType: GeometryType

    /** Array of the vertices. This buffer is _required_, but may empty. */
    var vertices: FloatBuffer
    /** Array of the normals. This buffer is _required_, and may _only_ be empty if [vertices] is empty as well. */
    var normals: FloatBuffer
    /** Array of the texture coordinates. Texture coordinates are optional. */
    var texcoords: FloatBuffer
    /** Array of the indices to create an indexed mesh. Optional, but advisable to use to minimize the number of submitted vertices. */
    var indices: IntBuffer

    /**
     * Recalculates normals, assuming CCW winding order and taking
     * STL's facet storage format into account.
     */
    fun recalculateNormals() {
        val vertexBufferView = vertices.asReadOnlyBuffer()
        var i = 0
        val normals = ArrayList<Float>()

        while (i < vertexBufferView.limit() - 1) {
            val v1 = GLVector(vertexBufferView[i], vertexBufferView[i + 1], vertexBufferView[i + 2])
            i += 3

            val v2 = GLVector(vertexBufferView[i], vertexBufferView[i + 1], vertexBufferView[i + 2])
            i += 3

            val v3 = GLVector(vertexBufferView[i], vertexBufferView[i + 1], vertexBufferView[i + 2])
            i += 3

            val a = v2 - v1
            val b = v3 - v1

            val n = a.cross(b).normalized

            normals.add(n.x())
            normals.add(n.y())
            normals.add(n.z())

            normals.add(n.x())
            normals.add(n.y())
            normals.add(n.z())

            normals.add(n.x())
            normals.add(n.y())
            normals.add(n.z())
        }

        this.normals = BufferUtils.allocateFloatAndPut(normals.toFloatArray())
    }
}
