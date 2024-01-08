package graphics.scenery.attribute.geometry

import graphics.scenery.*
import graphics.scenery.attribute.buffers.Buffers
import graphics.scenery.geometry.GeometryType
import graphics.scenery.utils.extensions.minus
import org.joml.Vector3f
import java.io.Serializable
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*

/**
 * Interface for any [Node] that stores geometry in the form of vertices,
 * normals, texcoords, or indices.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface Geometry : Buffers, Serializable {
    /** How many elements does a vertex store? */
    var vertexSize: Int
    /** How many elements does a texture coordinate store? */
    var texcoordSize: Int
    /** The [GeometryType] of the [Node] */
    var geometryType: GeometryType

    //vertices should be renamed to positions!
    /** Array of the vertices. This buffer is _required_, but may empty. */
    var vertices: FloatBuffer
    /** Array of the normals. This buffer is _required_, and may _only_ be empty if [vertices] is empty as well. */
    var normals: FloatBuffer
    /** Array of the texture coordinates. Texture coordinates are optional. */
    var texcoords: FloatBuffer
    /** Array of the indices to create an indexed mesh. Optional, but advisable to use to minimize the number of submitted vertices. */
    var indices: IntBuffer
    /** Whether the object is dirty and somehow needs to be updated. Used by renderers. */
    var dirty: Boolean
    /** Whether the [vertices]/[normals]/[indices] content is generated inside a compute shader. (Currently has no check. If this is true, vertex data gets ignored!)*/
    // TODO: should this lead to exclusive geometry generation or should the user still be able to predefine a vertex+index buffer in there class?
    var shaderSourced: Boolean

    fun generateBoundingBox(children: List<Node>): OrientedBoundingBox?

    /**
     * Recalculates normals, assuming CCW winding order and taking
     * STL's facet storage format into account.
     */
    fun recalculateNormals() {
        val vertexBufferView = vertices.asReadOnlyBuffer()
        var i = 0
        val normals = ArrayList<Float>()

        while (i < vertexBufferView.limit() - 1) {
            val v1 = Vector3f(vertexBufferView[i], vertexBufferView[i + 1], vertexBufferView[i + 2])
            i += 3

            val v2 = Vector3f(vertexBufferView[i], vertexBufferView[i + 1], vertexBufferView[i + 2])
            i += 3

            val v3 = Vector3f(vertexBufferView[i], vertexBufferView[i + 1], vertexBufferView[i + 2])
            i += 3

            val a = v2 - v1
            val b = v3 - v1

            val n = a.cross(b).normalize()

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
