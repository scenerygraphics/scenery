package scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import org.junit.Test
import scenery.*
import scenery.backends.Renderer
import scenery.controls.OpenVRInput
import scenery.backends.opengl.OpenGLShaderPreference
import scenery.repl.REPL
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

/**
 * Created by ulrik on 20/01/16.
 */
class BloodCellsExample : SceneryDefaultApplication("BloodCellsExample", windowWidth = 1280, windowHeight = 720) {
    private var ovr: OpenVRInput? = null

    override fun init() {
        try {
            ovr = OpenVRInput(seated = false, useCompositor = true)
            hub.add(SceneryElement.HMDINPUT, ovr!!)

            renderer = Renderer.createRenderer(applicationName, scene, windowWidth, windowHeight)
            hub.add(SceneryElement.RENDERER, renderer!!)

            val cam: Camera = DetachedHeadCamera()

            fun rangeRandomizer(min: Float, max: Float): Float = min + (Math.random().toFloat() * ((max - min) + 1.0f))

            var boxes = (0..10).map {
                Box(GLVector(0.5f, 0.5f, 0.5f))
            }

            var lights = (0..10).map {
                PointLight()
            }

            boxes.mapIndexed { i, box ->
                box.material = Material()
                box.addChild(lights[i])
                box.visible = false
                scene.addChild(box)
            }

            lights.map {
                it.position = GLVector(rangeRandomizer(-600.0f, 600.0f),
                        rangeRandomizer(-600.0f, 600.0f),
                        rangeRandomizer(-600.0f, 600.0f))
                it.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
                it.parent?.material?.diffuse = it.emissionColor
                it.intensity = 100.0f
                it.linear = 1f;
                it.quadratic = 0.1f;

                scene.addChild(it)
            }

            val hullbox = Box(GLVector(900.0f, 900.0f, 900.0f))
            hullbox.position = GLVector(0.1f, 0.1f, 0.1f)
            val hullboxM = Material()
            hullboxM.ambient = GLVector(1.0f, 1.0f, 1.0f)
            hullboxM.diffuse = GLVector(1.0f, 1.0f, 1.0f)
            hullboxM.specular = GLVector(1.0f, 1.0f, 1.0f)
            hullboxM.doubleSided = true
            hullbox.material = hullboxM

            scene.addChild(hullbox)

            val e_material = Material()
            e_material.ambient = GLVector(0.1f, 0.0f, 0.0f)
            e_material.diffuse = GLVector(0.4f, 0.0f, 0.02f)
            e_material.specular = GLVector(0.05f, 0f, 0f)
            e_material.doubleSided = false

            val erythrocyte = Mesh()
            erythrocyte.readFromOBJ(System.getenv("SCENERY_DEMO_FILES") + "/erythrocyte_simplified.obj")
            erythrocyte.material = e_material
            erythrocyte.name = "Erythrocyte_Master"
            scene.addChild(erythrocyte)

            erythrocyte.metadata.put(
                    "ShaderPreference",
                    OpenGLShaderPreference(
                            arrayListOf("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"),
                            HashMap(),
                            arrayListOf("DeferredShadingRenderer")))

            val l_material = Material()
            l_material.ambient = GLVector(0.1f, 0.0f, 0.0f)
            l_material.diffuse = GLVector(0.8f, 0.7f, 0.7f)
            l_material.specular = GLVector(0.05f, 0f, 0f)
            l_material.doubleSided = false

            val leucocyte = Mesh()
            leucocyte.readFromOBJ(System.getenv("SCENERY_DEMO_FILES") + "/leukocyte_simplified.obj")
            leucocyte.material = l_material
            leucocyte.name = "leucocyte_Master"
            scene.addChild(leucocyte)

            leucocyte.metadata.put(
                    "ShaderPreference",
                    OpenGLShaderPreference(
                            arrayListOf("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"),
                            HashMap(),
                            arrayListOf("DeferredShadingRenderer")))

            val posRange = 550.0f
            val container = Node("Cell container")

            val leucocytes = (0..200)
                    .map {
                        val v = Mesh()
                        v.name = "leucocyte_$it"
                        v.instanceOf = leucocyte
                        v
                    }
                    .map {
                        val p = Node("parent of it")
                        val scale = rangeRandomizer(30.0f, 40.0f)

                        it.material = l_material
                        it.scale = GLVector(scale, scale, scale)
                        it.children.forEach { ch -> ch.material = l_material }
                        it.rotation.setFromEuler(
                                rangeRandomizer(0.01f, 0.9f),
                                rangeRandomizer(0.01f, 0.9f),
                                rangeRandomizer(0.01f, 0.9f)
                        )

                        p.position = GLVector(rangeRandomizer(-posRange, posRange),
                                rangeRandomizer(-posRange, posRange), rangeRandomizer(-posRange, posRange))
                        p.addChild(it)

                        container.addChild(p)
                        it
                    }

            val bloodCells = (0..2000)
                    .map {
                        val v = Mesh()
                        v.name = "erythrocyte_$it"
                        v.instanceOf = erythrocyte

                        v
                    }
                    .map {
                        val p = Node("parent of it")
                        val scale = rangeRandomizer(5f, 12f)

                        it.material = e_material
                        it.scale = GLVector(scale, scale, scale)
                        it.children.forEach { ch -> ch.material = e_material }
                        it.rotation.setFromEuler(
                                rangeRandomizer(0.01f, 0.9f),
                                rangeRandomizer(0.01f, 0.9f),
                                rangeRandomizer(0.01f, 0.9f)
                        )

                        p.position = GLVector(rangeRandomizer(-posRange, posRange),
                                rangeRandomizer(-posRange, posRange), rangeRandomizer(-posRange, posRange))
                        p.addChild(it)

                        container.addChild(p)
                        it
                    }

            scene.addChild(container)

            cam.position = GLVector(0.0f, 0.0f, 0.0f)
            cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)

            cam.projection = GLMatrix().setPerspectiveProjectionMatrix(
                    50.0f / 180.0f * Math.PI.toFloat(),
                    windowWidth.toFloat() / windowHeight.toFloat(), 0.1f, 10000.0f)
            cam.active = true

            scene.addChild(cam)

            var ticks: Int = 0

            System.out.println(scene.children)

            fun hover(obj: Node, magnitude: Float, phi: Float) {
                obj.position = obj.position + GLVector(0.0f, magnitude * Math.cos(phi.toDouble() * 4).toFloat(), 0.0f)
            }

            fun hoverAndTumble(obj: Node, magnitude: Float, phi: Float, index: Int) {
                obj.parent.let {
                    obj.parent!!.position = obj.parent!!.position + GLVector(0.0f, magnitude * Math.cos(phi.toDouble() * 4).toFloat(), 0.0f)
                }

                val axis = GLVector(Math.sin(0.01 * index).toFloat(), -Math.cos(0.01 * index).toFloat(), index * 0.01f).normalized
                obj.rotation.rotateByAngleNormalAxis(magnitude, axis.x(), axis.y(), axis.z())
                obj.rotation.rotateByAngleY(-1.0f * magnitude)
            }

            thread {
                var reverse = false
                val step = 0.02f

                while (true) {
                    val phi = Math.PI * 2.0f * ticks / 2000.0f

                    boxes.mapIndexed {
                        i, box ->
                        //                                light.position.set(i % 3, step*10 * ticks)

                        box.position = GLVector(
                                Math.exp(i.toDouble()).toFloat() * 10 * Math.sin(phi).toFloat() + Math.exp(i.toDouble()).toFloat(),
                                step * ticks,
                                Math.exp(i.toDouble()).toFloat() * 10 * Math.cos(phi).toFloat() + Math.exp(i.toDouble()).toFloat())

                        box.children[0].position = box.position

                    }

                    bloodCells.forEachIndexed { i, bloodCell ->
                        hoverAndTumble(bloodCell, 0.003f, phi.toFloat(), i)
                        bloodCell.parent?.updateWorld(true)
                    }

                    leucocytes.forEachIndexed { i, leukocyte ->
                        hoverAndTumble(leukocyte, 0.001f, phi.toFloat() / 100.0f, i)
                    }

                    container.position = container.position - GLVector(0.1f, 0.1f, 0.1f)
                    container.updateWorld(true)


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

                    Thread.sleep(50)
                }
            }

            repl = REPL(scene, renderer!!)
            repl?.start()
            repl?.showConsoleWindow()
        } catch (e: Exception) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    @Test override fun main() {
        super.main()
    }
}
