package scenery.rendermodules.opengl

import cleargl.GLProgram
import cleargl.GLTexture
import scenery.NodeMetadata
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class OpenGLObjectState : NodeMetadata {
    override val consumers: MutableList<String> = ArrayList<String>()

    public var program: GLProgram? = null
    val additionalBufferIds = Hashtable<String, Int>()
    var textures = ConcurrentHashMap<String, GLTexture>()

    val mVertexArrayObject = IntArray(1)
    val mVertexBuffers = IntArray(3)
    val mIndexBuffer = IntArray(1)

    var isDynamic = true
    var initialized: Boolean = false

    var mStoredIndexCount = 0
    var mStoredPrimitiveCount = 0

    val mId: Int = 0

    constructor() {
       consumers.add("DeferredLightingRenderer")
       consumers.add("RenderGeometricalObject")
    }
}