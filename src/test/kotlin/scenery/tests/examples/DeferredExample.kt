package scenery.tests.examples

import cleargl.*
import com.jogamp.opengl.GLAutoDrawable
import com.jogamp.opengl.GLException
import org.junit.Test
import scenery.*
import scenery.controls.ClearGLInputHandler
import scenery.rendermodules.opengl.DeferredLightingRenderer
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
 */
class DeferredExample {


    private val scene: Scene = Scene()
    private var frameNum = 0
    private var deferredRenderer: DeferredLightingRenderer? = null

    @Test fun demo() {
        val lClearGLWindowEventListener = object : ClearGLDefaultEventListener() {

            private var mClearGLWindow: ClearGLDisplayable? = null

            override fun init(pDrawable: GLAutoDrawable) {
                super.init(pDrawable)
                try {
                    deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4,
                            mClearGLWindow!!.width,
                            mClearGLWindow!!.height)

                    val cam: Camera = DetachedHeadCamera()

                    fun rangeRandomizer(min: Float, max: Float): Float = min + (Math.random().toFloat() * ((max - min) + 1.0f))

                    var boxes = (1..20 step 1).map {
                        Box(GLVector(rangeRandomizer(0.5f, 4.0f),
                                rangeRandomizer(0.5f, 4.0f),
                                rangeRandomizer(0.5f, 4.0f)))
                    }

                    boxes.map { i ->
                        i.position =
                                GLVector(rangeRandomizer(-10.0f, 10.0f),
                                        rangeRandomizer(-10.0f, 10.0f),
                                        rangeRandomizer(-10.0f, 10.0f))
                        scene.addChild(i)
                    }

                    var lights = (0..64).map {
                        PointLight()
                    }

                    lights.map {
                        it.position = GLVector(rangeRandomizer(-600.0f, 600.0f),
                                rangeRandomizer(-600.0f, 600.0f),
                                rangeRandomizer(-600.0f, 600.0f))
                        it.emissionColor = GLVector(rangeRandomizer(0.0f, 1.0f),
                                rangeRandomizer(0.0f, 1.0f),
                                rangeRandomizer(0.0f, 1.0f))
                        it.intensity = rangeRandomizer(0.01f, 1000f)
                        it.linear = 0.1f;
                        it.quadratic = 0.01f;

                        scene.addChild(it)
                    }

                    var companionBox = Box(GLVector(5.0f, 5.0f, 5.0f))
                    companionBox.position = GLVector(1.0f, 1.0f, 1.0f)
                    companionBox.name = "Le Box de la Compagnion"
                    val companionBoxMaterial = Material()
                    companionBoxMaterial.ambient = GLVector(1.0f, 0.5f, 0.0f)
                    companionBoxMaterial.diffuse = GLVector(1.0f, 0.0f, 0.0f)
                    companionBoxMaterial.specular = GLVector(1.0f, 0.0f, 0.0f)

                    companionBox.material = companionBoxMaterial

                    boxes.first().addChild(companionBox)

                    val sphere = Sphere(0.5f, 20)
                    sphere.position = GLVector(0.5f, -1.2f, 0.5f)
                    sphere.scale = GLVector(5.0f, 5.0f, 5.0f)

                    val hullbox = Box(GLVector(100.0f, 100.0f, 100.0f))
                    hullbox.position = GLVector(0.0f, 0.0f, 0.0f)
                    val hullboxM = Material()
                    hullboxM.ambient = GLVector(0.6f, 0.6f, 0.6f)
                    hullboxM.diffuse = GLVector(0.4f, 0.4f, 0.4f)
                    hullboxM.specular = GLVector(0.0f, 0.0f, 0.0f)
                    hullbox.material = hullboxM
                    hullbox.doubleSided = true

//                    scene.addChild(hullbox)

                    val mesh = Mesh()
                    val meshM = Material()
                    meshM.ambient = GLVector(0.5f, 0.5f, 0.5f)
                    meshM.diffuse = GLVector(0.5f, 0.5f, 0.5f)
                    meshM.specular = GLVector(0.0f, 0.0f, 0.0f)

                    mesh.readFromOBJ("/Users/ulrik/Code/ClearVolume/scenery/models/sponza.obj")
                    //mesh.material = meshM
                    mesh.position = GLVector(155.5f, 150.5f, 55.0f)
                    mesh.scale = GLVector(0.1f, 0.1f, 0.1f)
                    mesh.updateWorld(true, true)
                    mesh.name = "Sponza_Mesh"

                    scene.addChild(mesh)

                    boxes.first().addChild(sphere)

                    cam.position = GLVector(0.0f, 0.0f, 0.0f)
                    cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)

                    cam.projection = GLMatrix().setPerspectiveProjectionMatrix(
                            70.0f / 180.0f * Math.PI.toFloat(),
                            pDrawable.surfaceWidth.toFloat() / pDrawable.surfaceHeight.toFloat(), 0.1f, 1000.0f).invert()
                    cam.active = true

                    scene.addChild(cam)

                    var ticks: Int = 0

                    System.out.println(scene.children)

                    thread {
                        var reverse = false
                        val step = 0.02f

                        while (true) {
                            boxes.mapIndexed {
                                i, box ->
                                box.position.set(i % 3, step * ticks)
                                box.needsUpdate = true
                            }

                            lights.mapIndexed {
                                i, light ->
//                                light.position.set(i % 3, step*10 * ticks)
                                val phi = Math.PI * 2.0f * ticks/500.0f

                                light.position = GLVector(
                                        i*10*Math.sin(phi).toFloat()+Math.exp(i.toDouble()).toFloat(),
                                        step*ticks,
                                        i*10*Math.cos(phi).toFloat()+Math.exp(i.toDouble()).toFloat())

                            }

                            if (ticks >= 5000 && reverse == false) {
                                reverse = true
                            }
                            if (ticks <= 0 && reverse == true) {
                                reverse = false
                            }

                            if (reverse) {
                                ticks--
                            } else {
                                ticks++
                            }

                            Thread.sleep(10)

                            boxes.first().rotation.rotateByEuler(0.01f, 0.0f, 0.0f)
                            boxes.first().needsUpdate = true
                            companionBox.needsUpdate = true
                            sphere.needsUpdate = true
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
                clearGLWindow.windowTitle =  "%.1f fps".format(pDrawable.animator?.lastFPS)
            }

            override fun setClearGLWindow(pClearGLWindow: ClearGLWindow) {
                mClearGLWindow = pClearGLWindow
            }

            override fun getClearGLWindow(): ClearGLDisplayable {
                return mClearGLWindow!!
            }

        }

        val lClearGLWindow = ClearGLWindow("scenery: DeferredExample",
                1280,
                720,
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

    @Test fun ScenegraphSimpleDemo() {

    }
}