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

    init {
		material = ShaderMaterial.fromClass(this::class.java, listOf(ShaderType.VertexShader, ShaderType.GeometryShader, ShaderType.FragmentShader))
		material.cullingMode = Material.CullingMode.None

	 	reshape(vector)
    }

    /**
     * Changes the shape of this arrow.
	  * TODO: make this method public
     *
     * @param p     The vector defining the shape of the arrow
     */
    fun reshape(vector: GLVector) {
		//init the data structures
	 	clearPoints()

		/** create the vector shape */
		//shortcut to null vector...
		var zeroGLvec = GLVector(0.0f,0.0f,0.0f)

		//first of the two mandatory surrounding fake points that are never displayed
		addPoint(zeroGLvec)

		//the main "vertical" segment of the vector
		addPoint(zeroGLvec)
		addPoint(vector)

		//the first triangle:
		//the shape of the triangle
		var V = 0.1f * vector.magnitude()

		//vector base is perpendicular to the input vector
		var base = GLVector(-vector.y(), vector.x(), 0.0f)
		var baseLen = base.magnitude()

		if (baseLen == 0.f) {
			//vector must be parallel to the z-axis, draw another perpendicular base
			base = GLVector(0.0f, 1.0f, 0.0f)
			baseLen = 1.0f
		}
		base.timesAssign(GLVector(V/baseLen,3))

		addPoint(vector.times(0.8f).plus(base))
		addPoint(vector.times(0.8f).minus(base))
		addPoint(vector)

		//the second triangle:
		base = base.cross(vector)
		base.timesAssign(GLVector(V/base.magnitude(),3))

		addPoint(vector.times(0.8f).plus(base))
		addPoint(vector.times(0.8f).minus(base))
		addPoint(vector)

		//second of the two mandatory surrounding fake points that are never displayed
		addPoint(vector)
	 }

    /**
     * Adds a line point to the line.
	  * TODO: make this method private
     *
     * @param p     The vector containing the vertex data
     */
    fun addPoint(p: GLVector) {
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

    /**
     * Fully clears the line.
	  * TODO: make this method private
     */
    fun clearPoints() {
        vertices.clear()
        normals.clear()
        texcoords.clear()

        vertices.limit(0)
        normals.limit(0)
        texcoords.limit(0)
    }
}
