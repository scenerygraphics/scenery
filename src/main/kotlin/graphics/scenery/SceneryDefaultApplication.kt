package graphics.scenery

import cleargl.ClearGLDefaultEventListener
import cleargl.ClearGLDisplayable
import cleargl.ClearGLWindow
import com.jogamp.opengl.GLAutoDrawable
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.backends.opengl.OpenGLRenderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.repl.REPL
import graphics.scenery.utils.Statistics
import org.slf4j.LoggerFactory

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

open class SceneryDefaultApplication(var applicationName: String,
                                     var windowWidth: Int = 1024,
                                     var windowHeight: Int = 1024,
                                     val wantREPL: Boolean = true) {

    /** The scene used by the renderer in the application */
    protected val scene: Scene = Scene()
    /** REPL for the application, can be initialised in the [init] function */
    protected var repl: REPL? = null
    /** Frame number for counting FPS */
    protected var frameNum = 0
    /** The Deferred Lighting Renderer for the application, see [OpenGLRenderer] */
    protected var renderer: Renderer? = null
    /** The Hub used by the application, see [Hub] */
    var hub: Hub = Hub()
    /** ui-behaviour input handler */
    protected var inputHandler: InputHandler? = null

    protected var stats: Statistics = Statistics(hub)

    protected var logger = LoggerFactory.getLogger(applicationName)

    var updateFunction: ()->Any = {}

    protected var running: Boolean = false

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
        hub.add(SceneryElement.STATISTICS, stats)

        if(wantREPL) {
            repl = REPL(scene, stats)
        }

        // initialize renderer, etc first in init, then setup key bindings
        init()

        repl?.addAccessibleObject(renderer!!)

        repl?.start()
        repl?.showConsoleWindow()

        val statsRequested = java.lang.Boolean.parseBoolean(System.getProperty("scenery.PrintStatistics", "false"))

        inputHandler = InputHandler(scene, renderer!!, hub)
        inputHandler?.useDefaultBindings(System.getProperty("user.home") + "/.$applicationName.bindings")

        // setup additional key bindings, if requested by the user
        inputSetup()

        var ticks = 0L

        running = true

        while(renderer!!.shouldClose == false) {
            val start = System.nanoTime()
            if(renderer!!.managesRenderLoop) {
                Thread.sleep(2)
            } else {
                stats.addTimed("render", { renderer!!.render() })
            }

            stats.addTimed("sceneUpdate", updateFunction)

            if(statsRequested && ticks % 100L == 0L) {
                logger.info("\nStatistics:\n=============\n${stats}")
            }

            val duration = System.nanoTime() - start*1.0f
            stats.add("loop", duration)

            ticks++
        }

        inputHandler?.close()
        renderer!!.close()
    }
}
