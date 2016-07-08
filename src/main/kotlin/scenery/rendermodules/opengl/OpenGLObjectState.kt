package scenery.rendermodules.opengl

import cleargl.GLProgram
import cleargl.GLTexture
import scenery.NodeMetadata
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenGLObjectState stores the OpenGL metadata that is needed for rendering a node
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates an empty OpenGLObjectState, with [DeferredLightingRenderer] as
 *  default consumers.
 */
class OpenGLObjectState : NodeMetadata {
    /** List of consumers of this metadata, e.g. [DeferredLightingRenderer] */
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

    constructor() {
        consumers.add("DeferredLightingRenderer")
    }
}
