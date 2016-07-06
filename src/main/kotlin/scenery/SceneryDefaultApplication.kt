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
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

open class SceneryDefaultApplication(var applicationName: String,
                                     var windowWidth: Int = 1024,
                                     var windowHeight: Int = 1024) {
    protected val scene: Scene = Scene()
    protected val repl: REPL = REPL()
    protected var frameNum = 0
    protected var deferredRenderer: DeferredLightingRenderer? = null
    protected var hub: Hub = Hub()
    protected var glWindow: ClearGLDisplayable? = null

    open fun init(pDrawable: GLAutoDrawable) {

    }

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
                var pHeight = pHeight

                if (pHeight == 0)
                    pHeight = 1

                super.reshape(pDrawable, pX, pY, pWidth, pHeight)
                deferredRenderer?.reshape(pWidth, pHeight)
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
    }
}
