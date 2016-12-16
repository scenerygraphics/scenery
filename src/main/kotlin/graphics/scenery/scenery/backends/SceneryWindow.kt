package graphics.scenery.scenery.backends

import cleargl.ClearGLDisplayable
import cleargl.ClearGLWindow

/**
 * Created by ulrik on 10/26/2016.
 */
class SceneryWindow {
    var glfwWindow: Long? = null
    var clearglWindow: ClearGLWindow? = null

    var width = 0
    var height = 0
}
