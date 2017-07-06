package graphics.scenery.backends

import javafx.application.Platform
import javafx.stage.Stage
import org.lwjgl.glfw.GLFW.*

/**
 * Abstraction class for GLFW, ClearGL and JavaFX windows
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
sealed class SceneryWindow {
    class UninitializedWindow : SceneryWindow()
    class GLFWWindow(var window: Long): SceneryWindow()
    class ClearGLWindow(var window: cleargl.ClearGLWindow): SceneryWindow()
    class JavaFXStage(var stage: Stage): SceneryWindow()

    var shouldClose = false

    var width = 0
    var height = 0
    var isFullscreen = false

    fun setTitle(title: String) {
        when(this) {
            is GLFWWindow -> glfwSetWindowTitle(window, title)
            is ClearGLWindow -> window.windowTitle = title
            is JavaFXStage -> {
                Platform.runLater { stage.title = title }
            }
        }
    }

    fun pollEvents() {
        if(this is GLFWWindow) {
            if (glfwWindowShouldClose(window)) {
                shouldClose = true
            }

            glfwPollEvents()
        }
    }
}
