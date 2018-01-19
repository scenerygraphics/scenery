package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.utils.Numerics
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BloodCellsExample : SceneryBase("BloodCellsExample", windowWidth = 1280, windowHeight = 720) {
    private var ovr: OpenVRHMD? = null

    override fun init() {
//        ovr = OpenVRHMD(seated = false, useCompositor = true)
//        hub.add(SceneryElement.HMDInput, ovr!!)

        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera()
        cam.perspectiveCamera(50.0f, 1.0f * windowWidth, 1.0f * windowHeight, 1.0f, 5000.0f)
        cam.active = true

        scene.addChild(cam)

        val lights = (0..10).map { PointLight() }

        lights.map {
            it.position = GLVector(Numerics.randomFromRange(-50.0f, 50.0f),
                Numerics.randomFromRange(-50.0f, 50.0f),
                Numerics.randomFromRange(-50.0f, 50.0f))
            it.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            it.intensity = 10.0f
            it.linear = 0.0f
            it.quadratic = 0.01f

            scene.addChild(it)
        }

        val hullMaterial = Material()
        hullMaterial.ambient = GLVector(0.0f, 0.0f, 0.0f)
        hullMaterial.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        hullMaterial.specular = GLVector(0.0f, 0.0f, 0.0f)
        hullMaterial.doubleSided = true

        val hull = Box(GLVector(1000.0f, 1000.0f, 1000.0f), insideNormals = true)
        hull.material = hullMaterial

        scene.addChild(hull)

        val e_material = Material()
        e_material.ambient = GLVector(0.1f, 0.0f, 0.0f)
        e_material.diffuse = GLVector(0.4f, 0.0f, 0.02f)
        e_material.specular = GLVector(0.05f, 0f, 0f)
        e_material.doubleSided = false

        val erythrocyte = Mesh()
        erythrocyte.readFromOBJ(getDemoFilesPath() + "/erythrocyte_simplified.obj")
        erythrocyte.material = e_material
        erythrocyte.name = "Erythrocyte_Master"
        erythrocyte.instanceMaster = true
        erythrocyte.instancedProperties.put("ModelMatrix", { erythrocyte.world })
//        erythrocyte.instancedProperties.put("ModelViewMatrix", { erythrocyte.modelView })
//        erythrocyte.instancedProperties.put("MVP", { erythrocyte.mvp })
        scene.addChild(erythrocyte)

        erythrocyte.material = ShaderMaterial(arrayListOf("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"))

        val l_material = Material()
        l_material.ambient = GLVector(0.1f, 0.0f, 0.0f)
        l_material.diffuse = GLVector(0.8f, 0.7f, 0.7f)
        l_material.specular = GLVector(0.05f, 0f, 0f)
        l_material.doubleSided = false

        val leucocyte = Mesh()
        leucocyte.readFromOBJ(getDemoFilesPath() + "/leukocyte_simplified.obj")
        leucocyte.material = l_material
        leucocyte.name = "leucocyte_Master"
        leucocyte.instanceMaster = true
        leucocyte.instancedProperties.put("ModelMatrix", { leucocyte.world})
//        leucocyte.instancedProperties.put("ModelViewMatrix", { leucocyte.modelView })
//        leucocyte.instancedProperties.put("MVP", { leucocyte.mvp })
        scene.addChild(leucocyte)

        leucocyte.material = ShaderMaterial(arrayListOf("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"))

        val posRange = 100.0f
        val container = Node("Cell container")

        val leucocytes = (0..100)
            .map {
                val v = Mesh()
                v.name = "leucocyte_$it"
                v.instanceOf = leucocyte
                v.instancedProperties.put("ModelMatrix", { v.world })

                val scale = Numerics.randomFromRange(1.5f, 2.0f)

                v.material = l_material
                v.scale = GLVector(scale, scale, scale)
                v.children.forEach { ch -> ch.material = l_material }
                v.rotation.setFromEuler(
                    Numerics.randomFromRange(0.01f, 0.9f),
                    Numerics.randomFromRange(0.01f, 0.9f),
                    Numerics.randomFromRange(0.01f, 0.9f)
                )

                v.position = Numerics.randomVectorFromRange(3, 0.0f, posRange)

                container.addChild(v)
                v
            }

        val erythrocytes = (0..2000)
            .map {
                val v = Mesh()
                v.name = "erythrocyte_$it"
                v.instanceOf = erythrocyte
                v.instancedProperties.put("ModelMatrix", { v.world })

                val scale = Numerics.randomFromRange(0.9f, 1.1f)

                v.material = e_material
                v.scale = GLVector(scale, scale, scale)
                v.children.forEach { ch -> ch.material = e_material }
                v.rotation.setFromEuler(
                    Numerics.randomFromRange(0.01f, 0.9f),
                    Numerics.randomFromRange(0.01f, 0.9f),
                    Numerics.randomFromRange(0.01f, 0.9f)
                )

                v.position = Numerics.randomVectorFromRange(3, 0.0f, posRange)

                container.addChild(v)
                v
            }

        scene.addChild(container)


        fun hoverAndTumble(obj: Node, magnitude: Float, phi: Float, index: Int) {
            val axis = GLVector(Math.sin(0.01 * index).toFloat(), -Math.cos(0.01 * index).toFloat(), index * 0.01f).normalized
            obj.rotation.rotateByAngleNormalAxis(magnitude, axis.x(), axis.y(), axis.z())
            obj.rotation.rotateByAngleY(-1.0f * magnitude)

            obj.needsUpdate = true
            obj.needsUpdateWorld = true
        }

        var ticks = 0
        launch {
            while(true) {
                val step = 0.05f
                val phi = Math.PI * 2.0f * ticks / 2000.0f

//            boxes.mapIndexed {
//                i, box ->
//                box.position = GLVector(
//                    Math.exp(i.toDouble()).toFloat() * 10 * Math.sin(phi).toFloat() + Math.exp(i.toDouble()).toFloat(),
//                    step * ticks,
//                    Math.exp(i.toDouble()).toFloat() * 10 * Math.cos(phi).toFloat() + Math.exp(i.toDouble()).toFloat())
//
//                box.children[0].position = box.position
//            }

                erythrocytes.mapIndexed { i, erythrocyte -> hoverAndTumble(erythrocyte, 0.003f, phi.toFloat(), i) }
                leucocytes.mapIndexed { i, leukocyte -> hoverAndTumble(leukocyte, 0.001f, phi.toFloat() / 100.0f, i) }

                container.position = container.position - GLVector(0.1f, 0.1f, 0.1f)

                container.updateWorld(true)
                ticks++

                delay(5, TimeUnit.MILLISECONDS)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
