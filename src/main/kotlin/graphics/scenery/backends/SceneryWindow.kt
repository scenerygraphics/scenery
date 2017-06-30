package graphics.scenery.backends

import cleargl.ClearGLWindow
import javafx.application.Platform
import javafx.stage.Stage
import org.lwjgl.glfw.GLFW.*

/**
 * Abstraction class for GLFW, ClearGL and JavaFX windows
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class SceneryWindow {
    var glfwWindow: Long? = null
    var clearglWindow: ClearGLWindow? = null
    var javafxStage: Stage? = null
    var shouldClose = false

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

        javafxStage?.let { window ->
            Platform.runLater { window.title = title }
        }
    }

    fun pollEvents() {
        glfwWindow?.let { window ->
            if (glfwWindowShouldClose(window)) {
                shouldClose = true
            }

            glfwPollEvents()
        }
    }
}
