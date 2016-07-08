package scenery

/**
 * Simple Mesh class to store geometry, inherits from [HasGeometry].
 * Can also be used for grouping objects easily.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Mesh() : Node("Mesh"), HasGeometry {
    /** Vertex storage array. Also see [HasGeometry] */
    override var vertices: FloatArray = floatArrayOf()
    /** Normal storage array. Also see [HasGeometry] */
    override var normals: FloatArray = floatArrayOf()
    /** Texcoord storage array. Also see [HasGeometry] */
    override var texcoords: FloatArray = floatArrayOf()
    /** Index storage array. Also see [HasGeometry] */
    override var indices: IntArray = intArrayOf()

    /** Vertex element size. Also see [HasGeometry] */
    override var vertexSize = 3;
    /** Texcoord element size. Also see [HasGeometry] */
    override var texcoordSize = 2;
    /** Geometry type of the Mesh. Also see [HasGeometry] and [GeometryType] */
    override var geometryType = GeometryType.TRIANGLES

    init {
    }


}
