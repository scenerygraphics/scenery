package scenery.tests.examples

import cleargl.*
import com.jogamp.opengl.GLAutoDrawable
import com.jogamp.opengl.GLException
import org.junit.Test
import org.scijava.Context
import org.scijava.`object`.ObjectService
import org.scijava.app.SciJavaApp
import org.scijava.plugin.Plugin
import org.scijava.ui.swing.script.AutoImporter
import org.scijava.ui.swing.script.InterpreterWindow
import scenery.*
import scenery.controls.ClearGLInputHandler
import scenery.rendermodules.opengl.DeferredLightingRenderer
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
 */
class BoxedProteinExample {


    private val scene: Scene = Scene()
    private var interpreter: InterpreterWindow? = null
    private var frameNum = 0
    private var deferredRenderer: DeferredLightingRenderer? = null
    private var hub: Hub = Hub()

    @Test fun demo() {
        val lClearGLWindowEventListener = object : ClearGLDefaultEventListener() {

            private var mClearGLWindow: ClearGLDisplayable? = null

            override fun init(pDrawable: GLAutoDrawable) {
                super.init(pDrawable)
                try {
                    deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4,
                            mClearGLWindow!!.width,
                            mClearGLWindow!!.height)
                    hub.add(SceneryElement.RENDERER, deferredRenderer!!)

                    val cam: Camera = DetachedHeadCamera()

                    fun rangeRandomizer(min: Float, max: Float): Float = min + (Math.random().toFloat() * ((max - min) + 1.0f))

                    var boxes = (0..2).map {
                        Box(GLVector(0.5f, 0.5f, 0.5f))
                    }

                    var lights = (0..2).map {
                        PointLight()
                    }

                    boxes.mapIndexed { i, box ->
                        box.material = Material()
                        box.addChild(lights[i])
                        scene.addChild(box)
                    }

                    lights.map {
                        it.position = GLVector(rangeRandomizer(-600.0f, 600.0f),
                                rangeRandomizer(-600.0f, 600.0f),
                                rangeRandomizer(-600.0f, 600.0f))
                        it.emissionColor = GLVector(rangeRandomizer(0.0f, 1.0f),
                                rangeRandomizer(0.0f, 1.0f),
                                rangeRandomizer(0.0f, 1.0f))
                        it.parent?.material?.diffuse = it.emissionColor
                        it.intensity = rangeRandomizer(0.01f, 500f)
                        it.linear = 0.01f;
                        it.quadratic = 0.01f;

                        scene.addChild(it)
                    }

                    val hullbox = Box(GLVector(100.0f, 100.0f, 100.0f))
                    hullbox.position = GLVector(0.0f, 0.0f, 0.0f)
                    val hullboxM = Material()
                    hullboxM.ambient = GLVector(0.6f, 0.6f, 0.6f)
                    hullboxM.diffuse = GLVector(0.4f, 0.4f, 0.4f)
                    hullboxM.specular = GLVector(0.0f, 0.0f, 0.0f)
                    hullboxM.doubleSided = true
                    hullbox.material = hullboxM

                    scene.addChild(hullbox)

                    val mesh = Mesh()
                    val meshM = Material()
                    meshM.ambient = GLVector(0.8f, 0.8f, 0.8f)
                    meshM.diffuse = GLVector(0.5f, 0.5f, 0.5f)
                    meshM.specular = GLVector(0.1f, 0f, 0f)

                    mesh.readFromOBJ(System.getenv("SCENERY_DEMO_FILES") + "/ORC6.obj")
//                    mesh.position = GLVector(155.5f, 150.5f, 55.0f)
                    mesh.position = GLVector(0.1f, 0.1f, 0.1f)
                    mesh.material = meshM
                    mesh.scale = GLVector(1.0f, 1.0f, 1.0f)
                    mesh.updateWorld(true, true)
                    mesh.name = "ORC6"
                    mesh.children.forEach { it.material = meshM }

                    scene.addChild(mesh)

                    cam.position = GLVector(0.0f, 0.0f, 0.0f)
                    cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)

                    cam.projection = GLMatrix().setPerspectiveProjectionMatrix(
                            50.0f / 180.0f * Math.PI.toFloat(),
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
                                //                                light.position.set(i % 3, step*10 * ticks)
                                val phi = Math.PI * 2.0f * ticks/500.0f

                                box.position = GLVector(
                                        Math.exp(i.toDouble()).toFloat() * 20 * Math.sin(phi).toFloat() + Math.exp(i.toDouble()).toFloat(),
                                        step*ticks,
                                        Math.exp(i.toDouble()).toFloat() * 20 * Math.cos(phi).toFloat() + Math.exp(i.toDouble()).toFloat())

                                box.children[0].position = box.position

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

//                            mesh.children[0].rotation.rotateByAngleX(0.001f)
//                            mesh.children[0].updateWorld(true, true)

                            Thread.sleep(10)
                        }
                    }

                    val context: Context = Context()
                    context.getService(ObjectService::class.java).addObject(scene)
                    context.getService(ObjectService::class.java).addObject(deferredRenderer)

                    interpreter = InterpreterWindow(context)
                    interpreter?.isVisible = true

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

                if (pHeight == 0)
                    pHeight = 1

                super.reshape(pDrawable, pX, pY, pWidth, pHeight)
                deferredRenderer?.reshape(pWidth, pHeight)
                val ratio = 1.0f * pWidth / pHeight
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

        val lClearGLWindow = ClearGLWindow("",
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

    @Plugin(type = AutoImporter::class)
    companion object ScenePlugin : AutoImporter {
        override fun getDefaultImports(): MutableMap<String, MutableList<String>>? {
            val name = SciJavaApp::class.simpleName!!
            val dot = name.lastIndexOf('.');
            val base = name.substring(dot + 1);
            System.out.println("Imported " + base + "; Try:\n\n\tprint(" + base + ".NAME);");

            val map: MutableMap<String, MutableList<String>> = HashMap<String, MutableList<String>>();
            map.put(name.substring(0, dot), Arrays.asList(base));
            return map;
        }

    }
}

