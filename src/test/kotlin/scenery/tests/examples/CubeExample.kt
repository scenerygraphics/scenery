package scenery.tests.examples

import cleargl.*
import com.jogamp.opengl.GLAutoDrawable
import com.jogamp.opengl.GLException
import org.junit.Test
import scenery.*
import scenery.rendermodules.opengl.DeferredLightingRenderer
import java.io.IOException
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class CubeExample {
    private val scene: Scene = Scene()
    private var frameNum = 0
    private var deferredRenderer: DeferredLightingRenderer? = null

    @Test fun demo() {
        val lClearGLWindowEventListener = object : ClearGLDefaultEventListener() {

            private var mClearGLWindow: ClearGLDisplayable? = null

            override fun init(pDrawable: GLAutoDrawable) {
                super.init(pDrawable)
                try {
                    deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4, mClearGLWindow!!.width, mClearGLWindow!!.height)

                    val cam: Camera = DetachedHeadCamera()

                    var box = Box(GLVector(1.0f, 1.0f, 1.0f))
                    var boxmaterial = PhongMaterial()
                    boxmaterial.ambient = GLVector(1.0f, 0.0f, 0.0f)
                    boxmaterial.diffuse = GLVector(0.0f, 1.0f, 0.0f)
                    boxmaterial.specular = GLVector(1.0f, 1.0f, 1.0f)
                    box.position = GLVector(0.0f, 0.0f, 1.1f)

                    scene.addChild(box)
                    scene.initList.add(box)

                    var lights = (0..2).map {
                        PointLight()
                    }

                    lights.mapIndexed { i, light ->
                        light.position = GLVector(2.0f*i, 2.0f*i, 2.0f*i)
                        light.emissionColor = GLVector(i*0.33f, 1.0f, 1.0f)
                        scene.addChild(light)
                    }

                    cam.position = GLVector(0.0f, 0.0f, -5.0f)
                    cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)
                    cam.projection = GLMatrix()
                            .setPerspectiveProjectionMatrix(
                                70.0f / 180.0f * Math.PI.toFloat(),
                                pDrawable.surfaceWidth.toFloat() / pDrawable.surfaceHeight.toFloat(), 0.1f, 1000.0f)
                            .invert()
                    cam.active = true

                    scene.addChild(cam)

                    thread {
                        while (true) {
                            box.rotation.rotateByEuler(0.0f, 0.01f, 0.01f)
                            box.needsUpdate = true

                            Thread.sleep(20)
                        }
                    }
                    deferredRenderer?.initializeScene(scene)
                } catch (e: GLException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }

            override fun reshape(pDrawable: GLAutoDrawable,
                                 pX: Int,
                                 pY: Int,
                                 pWidth: Int,
                                 pHeight: Int) {
                var pHeight = pHeight
                super.reshape(pDrawable, pX, pY, pWidth, pHeight)

                if (pHeight == 0)
                    pHeight = 1
                val ratio = 1.0f * pWidth / pHeight
            }

            override fun display(pDrawable: GLAutoDrawable) {
                super.display(pDrawable)

                frameNum++
                deferredRenderer?.render(scene)
                clearGLWindow.windowTitle = "%.1f fps".format(pDrawable.animator?.lastFPS)
            }

            override fun dispose(pDrawable: GLAutoDrawable) {
                super.dispose(pDrawable)
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

        lClearGLWindow.start()

        while (lClearGLWindow.isVisible) {
            Thread.sleep(10)
        }
    }
}
