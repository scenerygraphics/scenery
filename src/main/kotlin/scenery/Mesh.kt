package scenery

import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Simple Mesh class to store geometry, inherits from [HasGeometry].
 * Can also be used for grouping objects easily.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Mesh(override var name: String = "Mesh") : Node(name), HasGeometry {
    /** Vertex storage array. Also see [HasGeometry] */
    override var vertices: FloatBuffer = BufferUtils.allocateFloat(0)
    /** Normal storage array. Also see [HasGeometry] */
    override var normals: FloatBuffer = BufferUtils.allocateFloat(0)
    /** Texcoord storage array. Also see [HasGeometry] */
    override var texcoords: FloatBuffer = BufferUtils.allocateFloat(0)
    /** Index storage array. Also see [HasGeometry] */
    override var indices: IntBuffer = BufferUtils.allocateInt(0)

    /** Vertex element size. Also see [HasGeometry] */
    override var vertexSize = 3;
    /** Texcoord element size. Also see [HasGeometry] */
    override var texcoordSize = 2;
    /** Geometry type of the Mesh. Also see [HasGeometry] and [GeometryType] */
    override var geometryType = GeometryType.TRIANGLES

    init {
    }


}
