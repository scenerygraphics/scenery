package graphics.scenery.backends

import graphics.scenery.utils.SceneryPanel
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
    class JavaFXStage(var panel: SceneryPanel): SceneryWindow()

    var shouldClose = false

    var width = 0
    var height = 0
    var isFullscreen = false

    fun setTitle(title: String) {
        when(this) {
            is UninitializedWindow -> {}
            is GLFWWindow -> glfwSetWindowTitle(window, title)
            is ClearGLWindow -> window.windowTitle = title
            is JavaFXStage -> {
                Platform.runLater { (panel.scene.window as Stage).title = title }
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
