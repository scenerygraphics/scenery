package graphics.scenery

import cleargl.GLVector
import graphics.scenery.backends.ShaderType
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Class for creating 3D arrows, derived from [Node] and using [HasGeometry].
 * The arrow is defined with [vector] having the vector's foot/start as the
 * reference coordinate (arrow's foot will appear at the coordinate given to setPosition()).
 *
 *   /|\
 *  / | \
 * /--+--\
 *    |
 *    |
 *    |
 * The arrow is created as the main segment (drawn vertically bottom to up),
 * then 3 segments to build a triangle and then another triangle perpendicular
 * to the former one; altogehter 7 segments drawn sequentially.
 * The arrow head scales with the length of the arrow.
 *
 * @author Vladimir Ulman <ulman@mpi-cbg.de>
 */
class Arrow(var vector: GLVector) : Node("Arrow"), HasGeometry {
    /** Size of one vertex (e.g. 3 in 3D) */
    override val vertexSize: Int = 3
    /** Size of one texcoord (e.g. 2 in 3D) */
    override val texcoordSize: Int = 2
    /** Geometry type -- Default for Line is [GeometryType.LINE] */
    override var geometryType: GeometryType = GeometryType.LINE_STRIP_ADJACENCY
    /** Vertex buffer */
    override var vertices: FloatBuffer = BufferUtils.allocateFloat(30)
    /** Normal buffer */
    override var normals: FloatBuffer = BufferUtils.allocateFloat(30)
    /** Texcoord buffer */
    override var texcoords: FloatBuffer = BufferUtils.allocateFloat(20)
    /** Index buffer */
    override var indices: IntBuffer = IntBuffer.wrap(intArrayOf())

    /** Shader property for the line's starting segment color. Consumed by the renderer. */
    @ShaderProperty
    var startColor = GLVector(0.0f, 1.0f, 0.0f, 1.0f)

    /** Shader property for the line's color. Consumed by the renderer. */
    @ShaderProperty
    var lineColor = GLVector(1.0f, 1.0f, 1.0f, 1.0f)

    /** Shader property for the line's end segment color. Consumed by the renderer. */
    @ShaderProperty
    var endColor = GLVector(0.7f, 0.5f, 0.5f, 1.0f)

    /** Shader property for the line's cap length (start and end caps). Consumed by the renderer. */
    @ShaderProperty
    var capLength = 1

    /** Shader property to keep track of the current number of vertices. Consumed by the renderer. */
    @ShaderProperty
    var vertexCount: Int = 0
        private set

    /** Shader property for the line's edge width. Consumed by the renderer. */
    @ShaderProperty
    var edgeWidth = 2.0f

    //shortcut to null vector... to prevent from creating it anew with every call to reshape()
    private val zeroGLvec = GLVector(0.0f, 0.0f, 0.0f)

    init {
        material = ShaderMaterial.fromClass(Line::class.java, listOf(ShaderType.VertexShader, ShaderType.GeometryShader, ShaderType.FragmentShader))
        material.cullingMode = Material.CullingMode.None

        reshape(vector)
    }

    /**
     * Changes the shape of this arrow.
     *
     * @param p The vector defining the shape of the arrow
     */
    fun reshape(vector: GLVector) {
        //init the data structures
        clearPoints()

        /** create the vector shape */
        //first of the two mandatory surrounding fake points that are never displayed
        addPoint(zeroGLvec)

        //the main "vertical" segment of the vector
        addPoint(zeroGLvec)
        addPoint(vector)

        //the "horizontal" base segment of the "arrow head" triangles
        var base = zeroGLvec
        if (vector.x() == 0.0f && vector.y() == 0.0f) {
            //the input 'vector' must be parallel to the z-axis,
            //we can use this particular 'base' then
            base = GLVector(0.0f, 1.0f, 0.0f)
        }
        else {
            //vector 'base' is perpendicular to the input 'vector'
            base = GLVector(-vector.y(), vector.x(), 0.0f).normalize()
        }

        //the width of the "arrow head" triangle
        val V = 0.1f * vector.magnitude()

        //the first triangle:
        base = base.times(V)
        addPoint(vector.times(0.8f).plus(base))
        addPoint(vector.times(0.8f).minus(base))
        addPoint(vector)
        //NB: the 0.8f defines the height (1-0.8) of the "arrow head" triangle

        //the second triangle:
        base = base.cross(vector).normalize().times(V)
        addPoint(vector.times(0.8f).plus(base))
        addPoint(vector.times(0.8f).minus(base))
        addPoint(vector)

        //second of the two mandatory surrounding fake points that are never displayed
        addPoint(vector)
    }

    private fun addPoint(p: GLVector) {
        vertices.position(vertices.limit())
        vertices.limit(vertices.limit() + 3)
        vertices.put(p.toFloatArray())
        vertices.flip()

        normals.position(normals.limit())
        normals.limit(normals.limit() + 3)
        normals.put(p.toFloatArray())
        normals.flip()

        texcoords.position(texcoords.limit())
        texcoords.limit(texcoords.limit() + 2)
        texcoords.put(0.225f)
        texcoords.put(0.225f)
        texcoords.flip()

        dirty = true
        vertexCount = vertices.limit()/vertexSize

        boundingBox = generateBoundingBox()
    }

    private fun clearPoints() {
        vertices.clear()
        normals.clear()
        texcoords.clear()

        vertices.limit(0)
        normals.limit(0)
        texcoords.limit(0)
    }
}
