package graphics.scenery.primitives

import graphics.scenery.*
import org.joml.Vector3f
import org.joml.Vector4f
import graphics.scenery.backends.ShaderType
import graphics.scenery.geometry.GeometryType
import graphics.scenery.geometry.HasGeometry
import java.nio.FloatBuffer
import java.nio.IntBuffer

class LinePair @JvmOverloads constructor(var capacity: Int = 50, transparent: Boolean = false, val simple: Boolean = false) : Node("Line"), HasGeometry {
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

    /** Shader property for the interpolation state. */
    @ShaderProperty
    var interpolationState = 0.5f

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
     * Adds new positions to the line, plus their bundled "partner". They are currently stored in the normals array.
     * @param points1 Original points
     * @param points2 Bundled points
     */
    fun addPointPairs(points1: Array<Vector3f>, points2: Array<Vector3f>) {
        if(vertices.limit() + 3 * points1.size >= vertices.capacity()) {
            val newVertices = BufferUtils.allocateFloat(vertices.capacity() + points1.size * 3 + 3 * capacity)
            vertices.position(0)
            vertices.limit(vertices.capacity())
            newVertices.put(vertices)
            newVertices.limit(vertices.limit())

            vertices = newVertices

            val newNormals = BufferUtils.allocateFloat(vertices.capacity() + points2.size * 3 + 3 * capacity)
            normals.position(0)
            normals.limit(normals.capacity())
            newNormals.put(normals)
            newNormals.limit(normals.limit())

            normals = newNormals

            val newTexcoords = BufferUtils.allocateFloat(vertices.capacity() + points1.size * 2 + 2 * capacity)
            texcoords.position(0)
            texcoords.limit(texcoords.capacity())
            newTexcoords.put(texcoords)
            newTexcoords.limit(texcoords.limit())

            texcoords = newTexcoords

            capacity = vertices.capacity()/3
        }

        vertices.position(vertices.limit())
        vertices.limit(vertices.limit() + points1.size * 3)
        points1.forEach { v ->
            v.get(vertices)
            vertices.position(vertices.position() + 3)
        }
        vertices.flip()

        normals.position(normals.limit())
        normals.limit(normals.limit() + points2.size * 3)
        points2.forEach { v ->
            v.get(normals)
            normals.position(normals.position() + 3)
        }
        normals.flip()

        texcoords.position(texcoords.limit())
        texcoords.limit(texcoords.limit() + points1.size * 2)
        points1.forEach { _ ->
            texcoords.put(0.0f)
            texcoords.put(0.0f)
        }
        texcoords.flip()

        dirty = true
        vertexCount = vertices.limit()/vertexSize

        boundingBox = generateBoundingBox()
    }

}
