package scenery

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * Created by ulrik on 14/12/15.
 */
class Mesh() : Node("Mesh"), HasGeometry {
    override var vertices: FloatArray = floatArrayOf()
    override var normals: FloatArray = floatArrayOf()
    override var texcoords: FloatArray = floatArrayOf()
    override var indices: IntArray = intArrayOf()

    override val vertexSize = 3;
    override val texcoordSize = 2;
    override val geometryType = GeometryType.TRIANGLES;

    init {
    }


}
