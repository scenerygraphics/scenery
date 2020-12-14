package graphics.scenery.primitives

import graphics.scenery.*
import org.joml.Vector3f
import graphics.scenery.backends.ShaderType
import graphics.scenery.geometry.GeometryType
import graphics.scenery.geometry.HasGeometry
import graphics.scenery.numerics.Random
import org.joml.Vector4f
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Class for creating 3D lines, derived from [Node] and using [HasGeometry]
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Line @JvmOverloads constructor(var capacity: Int = 50, transparent: Boolean = false, val simple: Boolean = false) : Node("Line"), HasGeometry {
    /** Size of one vertex (e.g. 3 in 3D) */
    override val vertexSize: Int = 3
    /** Size of one texcoord (e.g. 2 in 3D) */
    override val texcoordSize: Int = 2
    /** Geometry type -- Default for Line is [GeometryType.LINE] */
    override var geometryType: GeometryType = GeometryType.LINE_STRIP_ADJACENCY
    /** Vertex buffer */
    override var vertices: FloatBuffer = BufferUtils.allocateFloat(3 * capacity)
    /** Normal buffer */
    override var normals: FloatBuffer = BufferUtils.allocateFloat(3 * capacity)
    /** Texcoord buffer */
    override var texcoords: FloatBuffer = BufferUtils.allocateFloat(2 * capacity)
    /** Index buffer */
    override var indices: IntBuffer = IntBuffer.wrap(intArrayOf())

    /** Whether the line should be rendered as transparent or not. */
    var transparent: Boolean = transparent
        set(value) {
            field = value
            activateTransparency(value)
        }

    /** Shader property for the line's starting segment color. Consumed by the renderer. */
    @ShaderProperty
    var startColor = Vector4f(0.0f, 1.0f, 0.0f, 1.0f)

    /** Shader property for the line's color. Consumed by the renderer. */
    @ShaderProperty
    var lineColor = Vector4f(1.0f, 1.0f, 1.0f, 1.0f)

    /** Shader property for the line's end segment color. Consumed by the renderer. */
    @ShaderProperty
    var endColor = Vector4f(0.7f, 0.5f, 0.5f, 1.0f)

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
        if(simple) {
            geometryType = GeometryType.LINE
        } else {
            activateTransparency(transparent)
        }

        vertices.limit(0)
        normals.limit(0)
        texcoords.limit(0)

        material.cullingMode = Material.CullingMode.None
    }

    protected fun activateTransparency(transparent: Boolean) {
        if(simple) {
            return
        }

        if(transparent) {
            val newMaterial = ShaderMaterial.fromFiles(
                "${this::class.java.simpleName}.vert",
                "${this::class.java.simpleName}.geom",
                "${this::class.java.simpleName}Forward.frag")

            newMaterial.blending.opacity = 1.0f
            newMaterial.blending.setOverlayBlending()
            newMaterial.diffuse = material.diffuse
            newMaterial.specular = material.specular
            newMaterial.ambient = material.ambient
            newMaterial.metallic = material.metallic
            newMaterial.roughness = material.roughness

            material = newMaterial
        } else {
            val newMaterial = ShaderMaterial.fromClass(this::class.java,
                listOf(ShaderType.VertexShader, ShaderType.GeometryShader, ShaderType.FragmentShader))

            newMaterial.diffuse = material.diffuse
            newMaterial.specular = material.specular
            newMaterial.ambient = material.ambient
            newMaterial.metallic = material.metallic
            newMaterial.roughness = material.roughness

            material = newMaterial
        }

        material.blending.transparent = transparent
        material.cullingMode = Material.CullingMode.None
    }

    /**
     * Adds points to the line.
     * If the line's vertex buffer cannot store all o the points,
     * a copy of it will be created that can store the additional points, plus
     * double it's initial capacity.
     *
     * @param points     The vector containing the points
     */
    fun addPoints(vararg points: Vector3f) {
        if(vertices.limit() + 3 * points.size >= vertices.capacity()) {
            val newVertices = BufferUtils.allocateFloat(vertices.capacity() + points.size * 3 + 3 * capacity)
            vertices.position(0)
            vertices.limit(vertices.capacity())
            newVertices.put(vertices)
            newVertices.limit(vertices.limit())

            vertices = newVertices

            val newNormals = BufferUtils.allocateFloat(vertices.capacity() + points.size * 3 + 3 * capacity)
            normals.position(0)
            normals.limit(normals.capacity())
            newNormals.put(normals)
            newNormals.limit(normals.limit())

            normals = newNormals


            val newTexcoords = BufferUtils.allocateFloat(vertices.capacity() + points.size * 2 + 2 * capacity)
            texcoords.position(0)
            texcoords.limit(texcoords.capacity())
            newTexcoords.put(texcoords)
            newTexcoords.limit(texcoords.limit())

            texcoords = newTexcoords

            capacity = vertices.capacity()/3
        }

        vertices.position(vertices.limit())
        vertices.limit(vertices.limit() + points.size * 3)
        points.forEach { v -> v.get(vertices) }
        vertices.position(vertices.limit())
        vertices.flip()

        normals.position(normals.limit())
        normals.limit(normals.limit() + points.size * 3)
        points.forEach { v -> v.get(normals) }
        normals.position(normals.limit())
        normals.flip()

        texcoords.position(texcoords.limit())
        texcoords.limit(texcoords.limit() + points.size * 2)
        points.forEach { _ ->
            texcoords.put(0.0f)
            texcoords.put(0.0f)
        }
        texcoords.position(texcoords.limit())
        texcoords.flip()

        dirty = true
        vertexCount = vertices.limit()/vertexSize

        boundingBox = generateBoundingBox()
    }

    /**
     * Add a point to the line.
     *
     * @param points     The vector containing the position of the point.
     */
    fun addPoint(point: Vector3f) {
        addPoints(point)
    }

    /**
     * Convenience function to add a list of vectors to the line.
     *
     * @param points A list of Vector3fs
     */
    fun addPoints(points: List<Vector3f>) {
        addPoints(*points.toTypedArray())
    }

    /**
     * Fully clears the line.
     */
    fun clearPoints() {
        vertices.clear()
        normals.clear()
        texcoords.clear()

        vertices.limit(0)
        normals.limit(0)
        texcoords.limit(0)
    }

    companion object {
        /**
         * Creates a set of lines with a specified number of points per line. The distance between points
         * can be set by [step] and the overall scale can be defined by [scale].
         * Note, the lines are generated with an additional random offset between them (to make the data
         * a bit less uniform).
         * @param numLines Number of lines to be generated
         * @param numPositions Number of positions to be generated per line
         * @param step The distance between two positions (i.e. to make a line longer)
         * @param scale The overall scale of the whole line set
         * @return An array of Line objects
         */
        @JvmStatic fun createLines(numLines: Int, numPositions: Int, step: Float, scale: Float): List<Line> {
            return (0 until numLines).map { i ->
                val line = Line()
                val randOffset = Random.randomFromRange(0.0f, 2.0f) - (numLines / 2).toFloat()
                for (j in -numPositions / 2 until numPositions / 2) {
                    line.addPoint(Vector3f(scale * (randOffset + i.toFloat()), scale * step * j.toFloat(), -100.0f))
                }
                line
            }
        }
    }
}
