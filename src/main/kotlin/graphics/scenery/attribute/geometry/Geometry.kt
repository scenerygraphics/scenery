package graphics.scenery.attribute.geometry

import graphics.scenery.*
import graphics.scenery.geometry.GeometryType
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.get
import graphics.scenery.utils.inc
import graphics.scenery.utils.set
import kool.*
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
interface Geometry : Serializable {
    /** How many elements does a vertex store? */
    var vertexSize: Int
    /** How many elements does a texture coordinate store? */
    var texcoordSize: Int
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
    /** Whether the object is dirty and somehow needs to be updated. Used by renderers. */
    var dirty: Boolean

    fun generateBoundingBox(children: List<Node>): OrientedBoundingBox?

    /**
     * Recalculates normals, assuming CCW winding order and taking
     * STL's facet storage format into account.
     */
    fun recalculateNormals() {
        var pVtx = vertices.adr.toPtr<Vector3f>()
        var pNorm = normals.adr.toPtr<Vector3f>()

        for (i in vertices.indices step 3) {
            val v1 = pVtx++[0]
            val v2 = pVtx++[0]
            val v3 = pVtx++[0]

            val a = v2 - v1
            val b = v3 - v1

            val n = a.cross(b).normalize()

            pNorm++[0] = n
            pNorm++[0] = n
            pNorm++[0] = n
        }
    }
}
