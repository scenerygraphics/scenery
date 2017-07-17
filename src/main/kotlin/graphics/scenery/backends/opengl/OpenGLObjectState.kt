package graphics.scenery.backends.opengl

import cleargl.GLProgram
import cleargl.GLTexture
import graphics.scenery.NodeMetadata
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenGLObjectState stores the OpenGL metadata that is needed for rendering a node
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates an empty OpenGLObjectState, with [OpenGLRenderer] as
 *  default consumers.
 */
class OpenGLObjectState : NodeMetadata {
    data class OpenGLBufferBinding(var buffer: ByteBuffer? = null, var offset: Long = 0L, var name: String, var binding: Int = 0)
    /** List of consumers of this metadata, e.g. [OpenGLRenderer] */
    override val consumers: MutableList<String> = ArrayList<String>()

    /** GLSL program for the Node */
    var program: GLProgram? = null
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

    /** Whether the object is dynamic, e.g. has its vertices regularly updated. */
    var isDynamic = true
    /** Whether the object has been initialised yet. */
    var initialized: Boolean = false

    /** Number of stores indices. */
    var mStoredIndexCount = 0
    /** Number of stored vertex/normal/texcoord primitives. */
    var mStoredPrimitiveCount = 0
    /** buffer bindings **/
    var bufferBindings = ArrayList<OpenGLBufferBinding>()
    /** OpenGL UBOs **/
    var UBOs = HashMap<String, OpenGLUBO>()
    /** are we missing textures? **/
    var defaultTexturesFor = HashSet<String>()
    /** shader to use for the program */
    var shader: GLProgram? = null

    /**
     * Default constructor, adding the [OpenGLRenderer]
     * to the list of consumers.
     */
    constructor() {
        consumers.add("OpenGLRenderer")
    }
}
