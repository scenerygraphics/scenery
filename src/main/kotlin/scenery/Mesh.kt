package scenery

/**
 * Created by ulrik on 14/12/15.
 */
open class Mesh() : Node("Mesh"), HasGeometry {
    override var vertices: FloatArray = floatArrayOf()
    override var normals: FloatArray = floatArrayOf()
    override var texcoords: FloatArray = floatArrayOf()
    override var indices: IntArray = intArrayOf()

    override var vertexSize = 3;
    override var texcoordSize = 2;
    override var geometryType = GeometryType.TRIANGLES

    init {
    }


}
