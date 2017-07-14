package graphics.scenery.backends.opengl

import cleargl.GLFramebuffer
import cleargl.GLProgram
import cleargl.GLVector
import graphics.scenery.backends.RenderConfigReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ulrik on 7/14/2017.
 */


class OpenGLRenderpass(var passName: String = "", var passConfig: RenderConfigReader.RenderpassConfig) {
    var openglMetadata: OpenGLMetadata = OpenGLMetadata()
    var output = ConcurrentHashMap<String, GLFramebuffer>()
    var inputs = ConcurrentHashMap<String, GLFramebuffer>()
    var defaultShader: GLProgram? = null

    data class Rect2D(var width: Int = 0, var height: Int = 0, var offsetX: Int = 0, var offsetY: Int = 0)
    data class Viewport(var area: Rect2D = Rect2D(), var minDepth: Float = 0.0f, var maxDepth: Float = 1.0f)
    data class ClearValue(var clearColor: GLVector = GLVector(0.0f, 0.0f, 0.0f, 1.0f), var clearDepth: Float = 0.0f)

    data class OpenGLMetadata(
        var scissor: Rect2D = Rect2D(),
        var renderArea: Rect2D = Rect2D(),
        var clearValues: ClearValue = ClearValue(),
        var viewport: Viewport = Viewport(),
        var eye: Int = 0
    )

    fun updateShaderParameters() {

    }
}
