package scenery

import cleargl.ClearGLDefaultEventListener
import cleargl.ClearGLDisplayable
import cleargl.ClearGLWindow
import com.jogamp.newt.awt.NewtCanvasAWT
import com.jogamp.newt.event.WindowEvent
import com.jogamp.opengl.GLAutoDrawable
import scenery.backends.Renderer
import scenery.backends.opengl.OpenGLRenderer
import scenery.controls.InputHandler
import scenery.repl.REPL
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import javax.swing.JFrame

/**
 * A default application to use scenery with, keeping the needed boilerplate
 * to a minimum. Inherit from this class for a quick start.
 *
 * @property[applicationName] Name of the application, do not use special chars
 * @property[windowWidth] Window width of the application window
 * @property[windowHeight] Window height of the application window
 *
 * @constructor Creates a new SceneryDefaultApplication
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

open class SceneryDefaultJPanelApplication(var applicationName: String,
                                     var windowWidth: Int = 1024,
                                     var windowHeight: Int = 1024) {

    /** The scene used by the renderer in the application */
    protected val scene: Scene = Scene()
    /** REPL for the application, can be initialised in the [init] function */
    protected var repl: REPL? = null
    /** Frame number for counting FPS */
    protected var frameNum = 0
    /** The Deferred Lighting Renderer for the application, see [Renderer] */
    protected var renderer: Renderer? = null
    /** The Hub used by the application, see [Hub] */
    var hub: Hub = Hub()
    /** ClearGL window used by the application, needs to be passed as a parameter to
     * the constructor of [OpenGLRenderer].
     */
    protected var glWindow: ClearGLDisplayable? = null
    /** ui-behaviour input handler */
    protected var inputHandler: InputHandler? = null
    /** Canvas used by the application */
    protected var glCanvas: NewtCanvasAWT? = null

    /**
     * the init function of [SceneryDefaultApplication], override this in your subclass,
     * e.g. for [Scene] constrution and [OpenGLRenderer] initialisation.
     *
     * @param[pDrawable] a [org.jogamp.jogl.GLAutoDrawable] handed over by [ClearGLDefaultEventListener]
     */
    open fun init() {

    }

    open fun inputSetup() {

    }

    /**
     * Main routine for [SceneryDefaultApplication]
     *
     * This routine will construct a internal [ClearGLDefaultEventListener], and initialize
     * with the [init] function. Override this in your subclass and be sure to call `super.main()`.
     *
     * The [ClearGLDefaultEventListener] will take care of usually used window functionality, like
     * resizing, closing, setting the OpenGL context, etc. It'll also read a keymap for the [InputHandler],
     * based on the [applicationName], from the file `~/.[applicationName].bindings
     *
     */
    open fun main() {
        // initialize renderer, etc first in init, then setup key bindings
        init()

        inputHandler = InputHandler(scene, renderer!!, hub)
        inputHandler?.useDefaultBindings(System.getProperty("user.home") + "/.$applicationName.bindings")

        // setup additional key bindings, if requested by the user
        inputSetup()


        // TODO: Add drawable back here!

        while(true) {
            renderer!!.render()
        }
    }
}
