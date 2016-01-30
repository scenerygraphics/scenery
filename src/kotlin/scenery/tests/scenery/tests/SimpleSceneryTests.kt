package scenery.tests

import cleargl.*
import com.jogamp.opengl.GL
import com.jogamp.opengl.GLAutoDrawable
import com.jogamp.opengl.GLException
import org.junit.Test
import scenery.*
import scenery.rendermodules.opengl.OpenGLRenderModule
import scenery.rendermodules.opengl.RenderGeometricalObject
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
 */
class SimpleSceneryTests {


    private val scene: Scene = Scene()
    private var objectMap = HashMap<Node, OpenGLRenderModule>()
    private var renderOrderList: ArrayList<OpenGLRenderModule> = ArrayList()

    private val renderMappings = hashMapOf(
            "HasGeometry" to RenderGeometricalObject::class
    )

    @Test
    public fun demo() {
        val lClearGLWindowEventListener = object : ClearGLDefaultEventListener() {

            private var mClearGLWindow: ClearGLDisplayable? = null

            override fun init(pDrawable: GLAutoDrawable) {
                super.init(pDrawable)
                try {
                    val lGL = pDrawable.gl
                    lGL.glEnable(GL.GL_DEPTH_TEST)
                    lGL.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

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
                        scene.initList.add(i)
                    }

                    var companionBox = Box(GLVector(5.0f, 5.0f, 5.0f))
                    companionBox.position = GLVector(-2.0f, 3.0f, 0.5f)
                    companionBox.rotation.rotateByEuler(2.4f, 1.2f, 0.5f)
                    companionBox.name = "Le Box de la Compagnion"
                    val companionBoxMaterial = PhongMaterial()
                    companionBoxMaterial.ambient = GLVector.getNullVector(3);
                    companionBoxMaterial.diffuse = GLVector(1.0f, 0.0f, 0.0f)
                    companionBoxMaterial.specular = GLVector.getNullVector(3);

                    companionBox.material = companionBoxMaterial
                    scene.initList.add(companionBox)

                    boxes.last().addChild(companionBox)

                    val sphere = Sphere(0.5f, 20)
                    sphere.position = GLVector(5.0f, -1.2f, 2.0f)

                    val hullbox = Box(GLVector(75.0f, 75.0f, 75.0f))
                    hullbox.position = GLVector(0.0f, 0.0f, 0.0f)
                    scene.initList.add(hullbox)

                    boxes.last().addChild(sphere)

                    val cam_view = GLMatrix.getIdentity()
                    cam_view.setCamera(-50.0f, -50.0f, -50.0f, 0.0f, -5.0f, -5.0f, 0.0f, 1.0f, 0.0f)
//                    cam_view.translate(-5.0f, -5.0f, 30.0f)

                    val cam_proj = GLMatrix()
                    cam_proj.setPerspectiveProjectionMatrix(
                            50.0f / 180.0f * Math.PI.toFloat(),
                            pDrawable.surfaceWidth.toFloat()/pDrawable.surfaceHeight.toFloat(), 0.1f, 1000.0f)
                    cam_proj.invert()

                    cam.projection = cam_proj
                    cam.view = cam_view
                    cam.active = true
                    cam.position = GLVector(0.0f, 0.0f, 0.0f)

                    scene.initList.add(sphere)

                    scene.addChild(cam)

                    var ticks: Int = 0

                    System.out.println(scene.children)

                    thread {
                        var reverse = false
                        val step = 0.02f

                        while (true) {
                            boxes.mapIndexed {
                                i, box ->
                                box.position!!.set(i % 3, step * ticks)
                            }

                            if (ticks >= 500 && reverse == false) {
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

                            Thread.sleep(20)

                            companionBox.rotation.z += 0.01f
                            companionBox.rotation.w -+ 0.01f
                        }
                    }

                    scene.initList.forEach {
                        o -> objectMap.put(o, renderMappings[o.javaClass.interfaces.first().toString().substringAfterLast(".")]!!.constructors.first().call(lGL, null, o))
                    }
                    objectMap.forEach { key, o -> o.initialize(); }
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
                // setPerspectiveProjectionMatrix(53.13f, ratio, 1.0f, 30.0f);
/*                clearGLWindow.setOrthoProjectionMatrix(-2f,
                        2f,
                        -2f,
                        2f,
                        10f,
                        -10f)*/
            }

            override fun display(pDrawable: GLAutoDrawable) {
                renderOrderList.clear()

                val cam: Camera = scene.findObserver()
                var mv: GLMatrix
                var mvp: GLMatrix
                var proj: GLMatrix

                super.display(pDrawable)

                val lGL = pDrawable.gl

                lGL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)

                scene.discover(scene, { n -> n is Renderable}).forEach {
                    objectMap[it]?.let {
                        renderOrderList.add(it)
                    }
                }

                renderOrderList.sort { a, b -> (a.node.position!!.z() - b.node.position!!.z()).toInt() }

                for(n in renderOrderList) {
//                    n.node.model = GLMatrix.getIdentity()
//                    n.node.model.translate(n.node.position!!.x(), n.node.position!!.y(), n.node.position!!.z())
//                    n.node.model.scale(n.node.scale!!.x(), n.node.scale!!.y(), n.node.scale!!.z())
                    n.node.updateWorld(true, false)

                    mv = cam.view!!.clone()
                    mv.mult(n.node.world)

                    proj = cam.projection!!.clone()
                    proj = cam.projection!!.clone()
                    mvp = proj.clone()
                    mvp.mult(mv)

                    n.program?.let {
                        n.program!!.use(lGL)
                        n.program!!.getUniform("ModelViewMatrix")!!.setFloatMatrix(mv, false)
                        n.program!!.getUniform("ProjectionMatrix")!!.setFloatMatrix(cam.projection, false)
                        n.program!!.getUniform("MVP")!!.setFloatMatrix(mvp, false)
                        n.program!!.getUniform("offset")!!.setFloatVector3(n.node.position?.toFloatBuffer())

                        n.program!!.getUniform("Light.Ld").setFloatVector3(1.0f, 1.0f, 0.8f);
                        n.program!!.getUniform("Light.Position").setFloatVector3(-5.0f, 5.0f, 5.0f);
                        n.program!!.getUniform("Light.La").setFloatVector3(0.4f, 0.4f, 0.4f);
                        n.program!!.getUniform("Light.Ls").setFloatVector3(0.0f, 0.0f, 0.0f);
                        n.program!!.getUniform("Material.Shinyness").setFloat(0.5f);

                        if(n.node.material != null) {
                            System.err.println("Using custom material for ${n.node.name}")
                            n.program!!.getUniform("Material.Ka").setFloatVector(n.node.material!!.ambient);
                            n.program!!.getUniform("Material.Kd").setFloatVector(n.node.material!!.diffuse);
                            n.program!!.getUniform("Material.Ks").setFloatVector(n.node.material!!.specular);
                        }
                        else {
                            System.err.println("Using default material for ${n.node.name}")
                            n.program!!.getUniform("Material.Ka").setFloatVector3(n.node.position?.toFloatBuffer());
                            n.program!!.getUniform("Material.Kd").setFloatVector3(n.node.position?.toFloatBuffer());
                            n.program!!.getUniform("Material.Ks").setFloatVector3(n.node.position?.toFloatBuffer());
                        }
                    }
                    n.draw()
                }

                clearGLWindow.windowTitle =  "%.1f fps".format(pDrawable.animator?.lastFPS)
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

        lClearGLWindowEventListener.isDebugMode = true

        val lClearGLWindow = ClearGLWindow("demo: ClearGLWindow",
                1280,
                720,
                lClearGLWindowEventListener)
        lClearGLWindow.isVisible = true
        lClearGLWindow.setFPS(300)
        lClearGLWindow.start()

        while (lClearGLWindow.isVisible) {
            Thread.sleep(10)
        }
    }

    @Test fun ScenegraphSimpleDemo() {

    }
}