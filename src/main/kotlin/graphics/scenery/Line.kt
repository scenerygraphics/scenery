package graphics.scenery

import cleargl.GLVector
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import kotlin.reflect.KProperty

/**
 * Class, deriving from Node for creating 3D lines
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Line : Node("Line"), HasGeometry {
    /** Array to store the current line points */
    var linePoints = ArrayList<Float>()
    /** Size of one vertex (e.g. 3 in 3D) */
    override val vertexSize: Int = 3
    /** Size of one texcoord (e.g. 2 in 3D) */
    override val texcoordSize: Int = 0
    /** Geometry type -- Default for Line is [GeometryType.LINE] */
    override var geometryType: GeometryType = GeometryType.LINE_STRIP_ADJACENCY
    /** Vertex buffer */
    override var vertices: FloatBuffer by this
    /** Normal buffer */
    override var normals: FloatBuffer by this
    /** Texcoord buffer */
    override var texcoords: FloatBuffer = FloatBuffer.wrap(floatArrayOf())
    /** Index buffer */
    override var indices: IntBuffer = IntBuffer.wrap(intArrayOf())

    /** Defines whether a class-name derived shader shall be used by the renderer, default true */
    override var useClassDerivedShader = true

    /** Shader property for the line's edge width. Consumed by the renderer. */
    @ShaderProperty
    var edgeWidth = 0.04f

    /** (Private) shader property to keep track of the current number of vertices. Consumed by the renderer. */
    @ShaderProperty
    private var vertexCount: Int = 0

    /** Shader property for the line's color. Consumed by the renderer. */
    @ShaderProperty
    var lineColor = GLVector(1.0f, 1.0f, 1.0f)

    /** Shader property for the line's starting segment color. Consumed by the renderer. */
    @ShaderProperty
    var startColor = GLVector(0.0f, 1.0f, 0.0f)

    /** Shader property for the line's end segment color. Consumed by the renderer. */
    @ShaderProperty
    var endColor = GLVector(0.7f, 0.5f, 0.5f)

    /** Shader property for the line's cap length (start and end caps). Consumed by the renderer. */
    @ShaderProperty
    var capLength = 1

    /**
     * Adds a line point to the line.
     *
     * @param p     The vector containing the vertex data
     */
    fun addPoint(p: GLVector) {
        linePoints.add(p.x())
        linePoints.add(p.y())
        linePoints.add(p.z())

        dirty = true
        vertexCount = linePoints.size/vertexSize
    }

    /**
     * Returns the line's current length, using a Euclidean metric.
     *
     * @returns The line's length as Float
     */
    fun getLength(): Float {
        var i = 0
        var len = 0.0f
        while(i < linePoints.size) {
            len += Math.sqrt(1.0*linePoints[i]*linePoints[i] + 1.0*linePoints[i+1]*linePoints[i+1] + 1.0*linePoints[i+2]*linePoints[i+2]).toFloat()
            i = i + 3
        }

        return len
    }

    /**
     * Removes the line point at the given index.
     *
     * @param index     The index of the point to remove from the line's vertex array.
     */
    fun removePointAtIndex(index: Int) {
        linePoints.removeAt(index*3 + 0)
        linePoints.removeAt(index*3 + 1)
        linePoints.removeAt(index*3 + 2)

        dirty = true
        vertexCount = linePoints.size/vertexSize
    }

    /**
     * Fully clears the line.
     */
    fun clearPoints() {
        linePoints.clear()
    }

    /**
     * Extension function required to use delegation
     */
    operator fun setValue(line: Line, property: KProperty<*>, floats: FloatBuffer) {
    }

    /**
     * Extension function of FloatBuffer to delegate buffer creation to this class.
     *
     * @param line      The line to create a FloatBuffer for
     * @param property  The requested property for which the FloatBuffer shall be created.
     *                  Currently, vertices and normals are supported.
     *
     * @returns FloatBuffer for storage.
     */
    operator fun getValue(line: Line, property: KProperty<*>): FloatBuffer {
        return when(property.name) {
            "vertices" -> {
                val buf: FloatBuffer = BufferUtils.allocateFloat(line.linePoints.size+6)
                buf.put(line.linePoints[0])
                buf.put(line.linePoints[1])
                buf.put(line.linePoints[2])

                buf.put(line.linePoints.toFloatArray())

                buf.put((line.linePoints[line.linePoints.size-3]))
                buf.put((line.linePoints[line.linePoints.size-2]))
                buf.put((line.linePoints[line.linePoints.size-1]))

                buf.flip()

                return buf

            }
            "normals" -> {
                val buf: FloatBuffer = BufferUtils.allocateFloat(line.linePoints.size+6)
                buf.put(line.linePoints[0])
                buf.put(line.linePoints[1])
                buf.put(line.linePoints[2])

                buf.put(line.linePoints.toFloatArray())

                buf.put((line.linePoints[line.linePoints.size-3]))
                buf.put((line.linePoints[line.linePoints.size-2]))
                buf.put((line.linePoints[line.linePoints.size-1]))

                buf.flip()

                return buf

            }
            else -> FloatBuffer.wrap(floatArrayOf())
        }
    }
}
