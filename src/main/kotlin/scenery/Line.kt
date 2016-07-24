package scenery

import cleargl.GLVector
import java.util.*
import kotlin.reflect.KProperty

/**
 * Class for creating 3D lines
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Line : Node("Line"), HasGeometry {
    var linePoints = ArrayList<Float>()
    override val vertexSize: Int = 3
    override val texcoordSize: Int = 0
    override val geometryType: GeometryType = GeometryType.LINE
    override var vertices: FloatArray by this
    override var normals: FloatArray by this

    override var texcoords: FloatArray = floatArrayOf()
    override var indices: IntArray = intArrayOf()

    override var useClassDerivedShader = true

    @ShaderProperty var edgeWidth = 0.004f
    @ShaderProperty private var vertexCount: Int = 0
    @ShaderProperty var lineColor = GLVector(1.0f, 1.0f, 1.0f, 1.0f)
    @ShaderProperty var startColor = GLVector(0.0f, 1.0f, 0.0f, 1.0f)
    @ShaderProperty var endColor = GLVector(0.7f, 0.5f, 0.5f, 1.0f)
    @ShaderProperty var capLength = 1

    fun addPoint(p: GLVector) {
        linePoints.add(p.x())
        linePoints.add(p.y())
        linePoints.add(p.z())

        dirty = true
        vertexCount = linePoints.size/vertexSize
    }

    fun removePointAtIndex(index: Int) {
        linePoints.removeAt(index*3 + 0)
        linePoints.removeAt(index*3 + 1)
        linePoints.removeAt(index*3 + 2)

        dirty = true
        vertexCount = linePoints.size/vertexSize
    }

    fun clearPoints() {
        linePoints.clear()
    }

    operator fun setValue(line: Line, property: KProperty<*>, floats: FloatArray) {
    }

    operator fun getValue(line: Line, property: KProperty<*>): FloatArray {
        return when(property.name) {
            "vertices" -> line.linePoints.toFloatArray()
            "normals" -> line.linePoints.toFloatArray()
            else -> floatArrayOf()
        }
    }
}
