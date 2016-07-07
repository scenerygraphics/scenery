package scenery

import cleargl.ClearGLDefaultEventListener
import cleargl.ClearGLDisplayable
import cleargl.ClearGLWindow
import com.jogamp.opengl.GLAutoDrawable
import org.junit.Test
import scenery.controls.ClearGLInputHandler
import scenery.rendermodules.opengl.DeferredLightingRenderer
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
    protected val repl: REPL = REPL()
    /** Frame number for counting FPS */
    protected var frameNum = 0
    /** The Deferred Lighting Renderer for the application, see [DeferredLightingRenderer] */
    protected var deferredRenderer: DeferredLightingRenderer? = null
    /** The Hub used by the application, see [Hub] */
    protected var hub: Hub = Hub()
    /** ClearGL window used by the application, needs to be passed as a parameter to
     * the constructor of [DeferredLightingRenderer].
     */
    protected var glWindow: ClearGLDisplayable? = null

    /**
     * the init function of [SceneryDefaultApplication], override this in your subclass,
     * e.g. for [Scene] constrution and [DeferredLightingRenderer] initialisation.
     *
     * @param[pDrawable] a [org.jogamp.jogl.GLAutoDrawable] handed over by [ClearGLDefaultEventListener]
     */
    open fun init(pDrawable: GLAutoDrawable) {

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
    @Test open fun main() {
        val lClearGLWindowEventListener = object : ClearGLDefaultEventListener() {

            override fun init(pDrawable: GLAutoDrawable) {
                this@SceneryDefaultApplication.init(pDrawable)
            }

            override fun display(pDrawable: GLAutoDrawable) {
                super.display(pDrawable)

                frameNum++
                deferredRenderer?.render(scene)

                if(deferredRenderer?.settings?.get<Boolean>("wantsFullscreen") == true && deferredRenderer?.settings?.get<Boolean>("isFullscreen") == false) {
                    glWindow!!.setFullscreen(true)
                    deferredRenderer?.settings?.set("wantsFullscreen", true)
                    deferredRenderer?.settings?.set("isFullscreen", true)
                }

                if(deferredRenderer?.settings?.get<Boolean>("wantsFullscreen") == false && deferredRenderer?.settings?.get<Boolean>("isFullscreen") == true) {
                    glWindow!!.setFullscreen(false)
                    deferredRenderer?.settings?.set("wantsFullscreen", false)
                    deferredRenderer?.settings?.set("isFullscreen", false)
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
                deferredRenderer?.reshape(pWidth, height)
            }

            override fun dispose(pDrawable: GLAutoDrawable) {
                System.err.println("Stopping with dispose")
                pDrawable.animator.stop()
                pDrawable.destroy()
            }

        }

        val glWindow = ClearGLWindow("",
                windowWidth,
                windowHeight,
                lClearGLWindowEventListener)
        glWindow.isVisible = true
        glWindow.setFPS(60)

        val inputHandler = ClearGLInputHandler(scene, deferredRenderer as Any, glWindow)
        inputHandler.useDefaultBindings(System.getProperty("user.home") + "/.$applicationName.bindings")

        glWindow.start()

        while (glWindow.isVisible) {
            Thread.sleep(10)
        }

        glWindow.stop()
    }
}
