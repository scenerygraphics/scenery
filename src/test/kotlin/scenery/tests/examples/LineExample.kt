package scenery.tests.examples

import cleargl.*
import com.jogamp.opengl.GLAutoDrawable
import org.junit.Test
import scenery.*
import scenery.controls.ClearGLInputHandler
import scenery.rendermodules.opengl.DeferredLightingRenderer

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class LineExample {
    private val scene: Scene = Scene()
    private var frameNum = 0
    private var deferredRenderer: DeferredLightingRenderer? = null

    @Test fun demo() {
        val lClearGLWindowEventListener = object : ClearGLDefaultEventListener() {

            private var mClearGLWindow: ClearGLDisplayable? = null

            override fun init(pDrawable: GLAutoDrawable) {
                super.init(pDrawable)
                deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4, mClearGLWindow!!.width, mClearGLWindow!!.height)

                var linematerial = Material()
                linematerial.ambient = GLVector(1.0f, 0.0f, 0.0f)
                linematerial.diffuse = GLVector(0.0f, 1.0f, 0.0f)
                linematerial.specular = GLVector(1.0f, 1.0f, 1.0f)

                var line = Line()
                line.addPoint(GLVector(-1.0f, -1.0f, -1.0f))
                line.addPoint(GLVector(0.0f, 1.0f, 0.0f))
                line.addPoint(GLVector(2.0f, 0.0f, 2.0f))
                line.addPoint(GLVector(10.0f, 5.0f, 10.0f))
                line.material = linematerial
                line.preDraw()
                line.position = GLVector(0.0f, 0.0f, 1.1f)

                scene.addChild(line)

                var lights = (0..2).map {
                    PointLight()
                }

                lights.mapIndexed { i, light ->
                    light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
                    light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
                    light.intensity = 0.8f*(i+1);
                    scene.addChild(light)
                }

                val cam: Camera = DetachedHeadCamera()
                cam.position = GLVector(0.0f, 0.0f, -5.0f)
                cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)
                cam.projection = GLMatrix()
                        .setPerspectiveProjectionMatrix(
                                70.0f / 180.0f * Math.PI.toFloat(),
                                pDrawable.surfaceWidth.toFloat() / pDrawable.surfaceHeight.toFloat(), 0.1f, 1000.0f)
                        .invert()
                cam.active = true

                scene.addChild(cam)

                deferredRenderer?.initializeScene(scene)
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

        }

        val lClearGLWindow = ClearGLWindow("scenery: CubeExample",
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
