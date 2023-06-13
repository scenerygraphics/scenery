package graphics.scenery.backends

import graphics.scenery.utils.SceneryJPanel
import org.lwjgl.glfw.GLFW.*
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Abstraction class for scenery windows.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class SceneryWindow {
    /** The default window state, before it becomes initialized to a specific window kind */
    class UninitializedWindow : SceneryWindow()
    /** GLFW window, with [window] being the pointer to GLFW's window object. */
    class GLFWWindow(var window: Long): SceneryWindow()
    /** Swing window with [panel] being the [SceneryJPanel] */
    class SwingWindow(var panel: SceneryJPanel): SceneryWindow()
    /** Headless window with no chrome whatsoever. */
    class HeadlessWindow : SceneryWindow()

    /** Whether the window should be closed on the next main loop iteration. */
    var shouldClose = false

    /** Window width, can only be set from package-internal functions. */
    var width = 0
        internal set
    /** Window height, can only be set from package-internal functions. */
    var height = 0
        internal set
    /** Window fullscreen state, can only be set from package-internal functions. */
    var isFullscreen = false
        internal set

    /** The window's title */
    open var title: String = ""
        set(value) {
            field = value
            when(this) {
                is UninitializedWindow -> {}
                is GLFWWindow -> glfwSetWindowTitle(window, value)
                is SwingWindow -> {
                    val window = SwingUtilities.getWindowAncestor(panel)
                    if(window != null) {
                        (window as? JFrame)?.title = value
                    }
                }
                is HeadlessWindow -> {}
            }
        }

    /**
     * Poll events function, in case the window system requires event polling.
     * (Only the case for GLFW so far)
     */
    fun pollEvents() {
        if(this is GLFWWindow) {
            if (glfwWindowShouldClose(window)) {
                shouldClose = true
            }

            glfwPollEvents()
        }
    }
}
