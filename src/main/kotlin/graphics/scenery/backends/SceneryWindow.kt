package graphics.scenery.backends

import cleargl.ClearGLWindow
import org.lwjgl.glfw.GLFW.glfwSetWindowTitle

/**
 * Created by ulrik on 10/26/2016.
 */
class SceneryWindow {
    var glfwWindow: Long? = null
    var clearglWindow: ClearGLWindow? = null

    var width = 0
    var height = 0
    var isFullscreen = false

    fun setTitle(title: String) {
        glfwWindow?.let { window ->
            glfwSetWindowTitle(window, title)
        }

        clearglWindow?.let { window ->
            window.windowTitle = title
        }
    }
}
