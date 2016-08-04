package scenery

import BufferUtils
import cleargl.GLVector
import java.nio.FloatBuffer
import java.nio.IntBuffer
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
    override var vertices: FloatBuffer by this
    override var normals: FloatBuffer by this

    override var texcoords: FloatBuffer = FloatBuffer.wrap(floatArrayOf())
    override var indices: IntBuffer = IntBuffer.wrap(intArrayOf())

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

    operator fun setValue(line: Line, property: KProperty<*>, floats: FloatBuffer) {
    }

    operator fun getValue(line: Line, property: KProperty<*>): FloatBuffer {
        return when(property.name) {
            "vertices" -> BufferUtils.allocateFloatAndPut(line.linePoints.toFloatArray())
            "normals" -> BufferUtils.allocateFloatAndPut(line.linePoints.toFloatArray())
            else -> FloatBuffer.wrap(floatArrayOf())
        }
    }
}
