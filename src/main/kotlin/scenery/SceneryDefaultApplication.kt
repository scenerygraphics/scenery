package scenery

import cleargl.ClearGLDefaultEventListener
import cleargl.ClearGLDisplayable
import cleargl.ClearGLWindow
import com.jogamp.opengl.GLAutoDrawable
import scenery.backends.Renderer
import scenery.backends.opengl.DeferredLightingRenderer
import scenery.controls.ClearGLInputHandler
import scenery.repl.REPL

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
                                     var windowHeight: Int = 1024) {

    /** The scene used by the renderer in the application */
    protected val scene: Scene = Scene()
    /** REPL for the application, can be initialised in the [init] function */
    protected var repl: REPL? = null
    /** Frame number for counting FPS */
    protected var frameNum = 0
    /** The Deferred Lighting Renderer for the application, see [DeferredLightingRenderer] */
    protected var renderer: Renderer? = null
    /** The Hub used by the application, see [Hub] */
    var hub: Hub = Hub()
    /** ClearGL window used by the application, needs to be passed as a parameter to
     * the constructor of [DeferredLightingRenderer].
     */
    protected var glWindow: ClearGLDisplayable? = null
    /** ui-behaviour input handler */
    protected var inputHandler: ClearGLInputHandler? = null

    /**
     * the init function of [SceneryDefaultApplication], override this in your subclass,
     * e.g. for [Scene] constrution and [DeferredLightingRenderer] initialisation.
     *
     * @param[pDrawable] a [org.jogamp.jogl.GLAutoDrawable] handed over by [ClearGLDefaultEventListener]
     */
    open fun init(pDrawable: GLAutoDrawable) {

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
     * resizing, closing, setting the OpenGL context, etc. It'll also read a keymap for the [ClearGLInputHandler],
     * based on the [applicationName], from the file `~/.[applicationName].bindings
     *
     */
    open fun main() {
        val lClearGLWindowEventListener = object : ClearGLDefaultEventListener() {

            override fun init(pDrawable: GLAutoDrawable) {
                this@SceneryDefaultApplication.init(pDrawable)

                inputHandler = ClearGLInputHandler(scene, renderer as Any, glWindow!!, hub)
                inputHandler?.useDefaultBindings(System.getProperty("user.home") + "/.$applicationName.bindings")

                this@SceneryDefaultApplication.inputSetup()
            }

            override fun display(pDrawable: GLAutoDrawable) {
                super.display(pDrawable)

                frameNum++
                renderer?.render(scene)

                if(renderer?.settings?.get<Boolean>("wantsFullscreen") == true && renderer?.settings?.get<Boolean>("isFullscreen") == false) {
                    glWindow!!.setFullscreen(true)
                    renderer?.settings?.set("wantsFullscreen", true)
                    renderer?.settings?.set("isFullscreen", true)
                }

                if(renderer?.settings?.get<Boolean>("wantsFullscreen") == false && renderer?.settings?.get<Boolean>("isFullscreen") == true) {
                    glWindow!!.setFullscreen(false)
                    renderer?.settings?.set("wantsFullscreen", false)
                    renderer?.settings?.set("isFullscreen", false)
                }

                clearGLWindow.windowTitle = "scenery: %s - %.1f fps".format(applicationName, pDrawable.animator?.lastFPS)
            }

            override fun setClearGLWindow(pClearGLWindow: ClearGLWindow) {
                glWindow = pClearGLWindow
            }

            override fun getClearGLWindow(): ClearGLDisplayable {
                return glWindow!!
            }

            override fun reshape(pDrawable: GLAutoDrawable,
                                 pX: Int,
                                 pY: Int,
                                 pWidth: Int,
                                 pHeight: Int) {
                var height = pHeight

                if (height == 0)
                    height = 1

                super.reshape(pDrawable, pX, pY, pWidth, height)
                renderer?.reshape(pWidth, height)
            }

            override fun dispose(pDrawable: GLAutoDrawable) {
                System.err.println("Stopping with dispose")
                pDrawable.animator?.stop()
            }

        }

        val glWindow = ClearGLWindow("",
                windowWidth,
                windowHeight,
                lClearGLWindowEventListener)
        glWindow.isVisible = true
        glWindow.setFPS(60)

        glWindow.start()

        while (glWindow.isVisible) {
            Thread.sleep(10)
        }

        glWindow.stop()
    }
}
