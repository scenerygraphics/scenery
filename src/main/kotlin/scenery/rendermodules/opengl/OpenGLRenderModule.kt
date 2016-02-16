package scenery.rendermodules.opengl

import cleargl.GLProgram
import scenery.Node

/**
 * Created by ulrik on 20/01/16.
 */
interface OpenGLRenderModule {
    var program: GLProgram?
    var node: Node
    fun initialize(): Boolean
    fun draw()
}