package graphics.scenery.backends.opengl

import cleargl.GLTexture
import graphics.scenery.NodeMetadata
import graphics.scenery.utils.LazyLogger
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashSet

/**
 * OpenGLObjectState stores the OpenGL metadata that is needed for rendering a node
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates an empty OpenGLObjectState, with [OpenGLRenderer] as
 *  default consumers.
 */
class OpenGLObjectState : NodeMetadata {
    private val logger by LazyLogger()
    /** List of consumers of this metadata, e.g. [OpenGLRenderer] */
    override val consumers: MutableList<String> = ArrayList()

    /** IDs of buffers that may be additionally required. */
    val additionalBufferIds = Hashtable<String, Int>()
    /** Hash map of GLTexture objects storing the OpenGL texture handles. */
    var textures = ConcurrentHashMap<String, GLTexture>()

    /** VAO storage. */
    val mVertexArrayObject = IntArray(1)
    /** VBO storage. */
    val mVertexBuffers = IntArray(3)
    /** Index buffer storage. */
    val mIndexBuffer = IntArray(1)

    /** Whether the object has been initialised yet. */
    var initialized: Boolean = false

    /** Number of stores indices. */
    var mStoredIndexCount = 0
    /** Number of stored vertex/normal/texcoord primitives. */
    var mStoredPrimitiveCount = 0
    /** OpenGL UBOs **/
    var UBOs = LinkedHashMap<String, OpenGLUBO>()
    /** are we missing textures? **/
    var defaultTexturesFor = HashSet<String>()
    /** shader to use for the program */
    var shader: OpenGLShaderProgram? = null
    /** instance count */
    var instanceCount: Int = 1
    /** buffer storage */
    var vertexBuffers = HashMap<String, ByteBuffer>()
    /** Hash code for the currently used material */
    var materialHash: Int = -1
    /** Last reload time for textures */
    var texturesLastSeen = 0L

    init {
        consumers.add("OpenGLRenderer")
    }

    /**
     * Returns the UBO given by [name] if it exists, otherwise null.
     */
    fun getUBO(name: String): OpenGLUBO? {
        return UBOs[name]
    }

    /**
     * Returns the UBO given by [name] if it exists and has a backing buffer, otherwise null.
     */
    fun getBackedUBO(name: String): Pair<OpenGLUBO, OpenGLRenderer.OpenGLBuffer>? {
        val ubo = UBOs[name]
        return if(ubo?.backingBuffer != null) {
            ubo to ubo.backingBuffer
        } else {
            logger.warn("UBO for $name has no backing buffer")
            null
        }
    }
}
