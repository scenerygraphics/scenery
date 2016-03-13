package scenery.rendermodules.opengl

import cleargl.GLProgram
import cleargl.GLTexture
import scenery.NodeMetadata
import java.util.*

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class OpenGLObjectState : NodeMetadata {
    override val consumers: MutableList<String> = ArrayList<String>()

    var program: GLProgram? = null
    val additionalBufferIds = Hashtable<String, Int>()
    val textures = HashMap<String, GLTexture>()

    val mVertexArrayObject = IntArray(1)
    val mVertexBuffers = IntArray(3)
    val mIndexBuffer = IntArray(1)

    var isDynamic = false
    var initialized: Boolean = false

    var mStoredIndexCount = 0
    var mStoredPrimitiveCount = 0

    val mId: Int = 0

    constructor() {
       consumers.add("DeferredLightingRenderer")
       consumers.add("RenderGeometricalObject")
    }
}