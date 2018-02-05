package graphics.scenery.backends.opengl

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

    fun populate(offset: Long = 0) {
        backingBuffer?.let { data ->
            super.populate(data.buffer, 0L, elements = null)
        }
    }
}
