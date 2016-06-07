package scenery.tests.examples

import cleargl.*
import com.jogamp.opengl.GLAutoDrawable
import org.junit.Test
import org.scijava.Context
import org.scijava.`object`.ObjectService
import org.scijava.ui.swing.script.InterpreterWindow
import scenery.*
import scenery.controls.ClearGLInputHandler
import scenery.rendermodules.opengl.DeferredLightingRenderer
import java.util.*
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TexturedCubeExample {
    private val scene: Scene = Scene()
    private var frameNum = 0
    private var deferredRenderer: DeferredLightingRenderer? = null
    private var interpreter: InterpreterWindow? = null

    @Test fun demo() {
        val lClearGLWindowEventListener = object : ClearGLDefaultEventListener() {

            private var mClearGLWindow: ClearGLDisplayable? = null

            override fun init(pDrawable: GLAutoDrawable) {
                super.init(pDrawable)
                deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4, mClearGLWindow!!.width, mClearGLWindow!!.height)

                var box = Box(GLVector(1.0f, 1.0f, 1.0f))
                var boxmaterial = Material()
                boxmaterial.ambient = GLVector(1.0f, 0.0f, 0.0f)
                boxmaterial.diffuse = GLVector(0.0f, 1.0f, 0.0f)
                boxmaterial.specular = GLVector(1.0f, 1.0f, 1.0f)
                boxmaterial.textures.put("diffuse", this.javaClass.getResource("textures/helix.png").file)
                box.material = boxmaterial

                box.position = GLVector(0.0f, 0.0f, 0.0f)
                scene.addChild(box)

                var lights = (0..2).map {
                    PointLight()
                }

                lights.mapIndexed { i, light ->
                    light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
                    light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
                    light.intensity = 0.2f*(i+1);
                    scene.addChild(light)
                }

                val cam: Camera = DetachedHeadCamera()
                cam.position = GLVector(0.0f, 0.0f, -5.0f)
                cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)
                cam.projection = GLMatrix()
                        .setPerspectiveProjectionMatrix(
                                70.0f / 180.0f * Math.PI.toFloat(),
                                1024f / 1024f, 0.1f, 1000.0f)
                        .invert()
                cam.active = true

                scene.addChild(cam)

                thread {
                    while (true) {
                        box.rotation.rotateByAngleY(0.01f)
                        box.needsUpdate = true

                        Thread.sleep(20)
                    }
                }

                val context: Context = Context()
                context.getService(ObjectService::class.java).addObject(scene)
                context.getService(ObjectService::class.java).addObject(deferredRenderer)

                interpreter = InterpreterWindow(context)
                interpreter?.isVisible = true


                deferredRenderer?.initializeScene(scene)

                val startup = Scanner(DeferredLightingRenderer::class.java.getResourceAsStream("startup.js"), "UTF-8").useDelimiter("\\A").next()
                interpreter?.repl?.interpreter?.eval(startup)
            }

            override fun display(pDrawable: GLAutoDrawable) {
                super.display(pDrawable)

                frameNum++
                deferredRenderer?.render(scene)
                clearGLWindow.windowTitle = "scenery: %s - %.1f fps".format(this.javaClass.enclosingClass.simpleName.substringAfterLast("."), pDrawable.animator?.lastFPS)
            }

            override fun setClearGLWindow(pClearGLWindow: ClearGLWindow) {
                mClearGLWindow = pClearGLWindow
            }

            override fun getClearGLWindow(): ClearGLDisplayable {
                return mClearGLWindow!!
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

        val lClearGLWindow = ClearGLWindow("",
                1024,
                1024,
                lClearGLWindowEventListener)
        lClearGLWindow.isVisible = true
        lClearGLWindow.setFPS(60)

        val inputHandler = ClearGLInputHandler(scene, deferredRenderer as Any, lClearGLWindow)
        inputHandler.useDefaultBindings(System.getProperty("user.home") + "/.sceneryExamples.bindings")

        lClearGLWindow.start()

        while (lClearGLWindow.isVisible) {
            Thread.sleep(10)
        }
    }
}
