package graphics.scenery.backends.opengl

import graphics.scenery.Node
import graphics.scenery.backends.UBO
import java.nio.ByteBuffer

/**
 * UBO class for OpenGL
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class OpenGLUBO(val backingBuffer: OpenGLRenderer.OpenGLBuffer? = null) : UBO() {
    var offset = 0
    var binding = 0

    @Suppress("UNUSED_PARAMETER")
    fun populate(offset: Long = 0): Boolean {
        backingBuffer?.let { data ->
            return super.populate(data.buffer, -1L, elements = null)
        }

        return false
    }

    fun populateParallel(bufferView: ByteBuffer, offset: Long, elements: LinkedHashMap<String, () -> Any>): Boolean {
        bufferView.position(0)
        bufferView.limit(bufferView.capacity())
        return super.populate(bufferView, offset, elements)
    }

    fun fromInstance(node: Node) {
        node.instancedProperties.forEach { members.putIfAbsent(it.key, it.value) }
    }

    fun setOffsetFromBackingBuffer() {
        backingBuffer?.let {
            offset = it.advance()
        }
    }
}
