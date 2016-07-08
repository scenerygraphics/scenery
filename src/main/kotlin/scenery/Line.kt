package scenery

import cleargl.GLVector
import java.util.*

/**
 * Class for creating 3D lines
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Line : Node("Line"), HasGeometry {
    override val vertexSize: Int = 3
    override val texcoordSize: Int = 0
    override val geometryType: GeometryType = GeometryType.LINE
    override var vertices: FloatArray = floatArrayOf()
    override var normals: FloatArray = floatArrayOf()
    override var texcoords: FloatArray = floatArrayOf()
    override var indices: IntArray = intArrayOf()

    override var useClassDerivedShader = true

    var linePoints = ArrayList<Float>()

    fun addPoint(p: GLVector) {
        linePoints.add(p.x())
        linePoints.add(p.y())
        linePoints.add(p.z())
    }

    fun removePointAtIndex(index: Int) {
        linePoints.removeAt(index*3 + 0)
        linePoints.removeAt(index*3 + 1)
        linePoints.removeAt(index*3 + 2)
    }

    fun clearPoints() {
        linePoints.clear()
    }

    override fun preDraw() {
        this.vertices = linePoints.toFloatArray()
        this.normals = this.vertices
    }
}
