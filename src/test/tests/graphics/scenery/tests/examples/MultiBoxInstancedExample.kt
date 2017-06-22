package graphics.scenery.tests.examples

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.ShaderPreference
import graphics.scenery.utils.Numerics
import org.junit.Test
import java.util.*
import kotlin.concurrent.thread

/**
 * Demo animating multiple boxes with instancing.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class MultiBoxInstancedExample : SceneryDefaultApplication("MultiBoxInstancedExample") {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(10.0f, 10.0f, 10.0f)
            perspectiveCamera(60.0f, 1.0f * windowWidth, 1.0f * windowHeight, 1.0f, 1000.0f)
            active = true

            scene.addChild(this)
        }

        val boundaryWidth = 15.0
        val boundaryHeight = 15.0

        val container = Mesh()

        val b = Box(GLVector(0.2f, 0.2f, 0.2f))
        b.material = Material()
        b.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        b.material.ambient = GLVector(1.0f, 1.0f, 1.0f)
        b.material.specular = GLVector(1.0f, 1.0f, 1.0f)
        b.name = "boxmaster"

        b.instanceMaster = true
        b.instancedProperties.put("ModelViewMatrix", { b.modelView })
        b.instancedProperties.put("ModelMatrix", { b.model })
        b.instancedProperties.put("MVP", { b.mvp })
        b.metadata.put(
            "ShaderPreference",
            ShaderPreference(
                arrayListOf("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"),
                HashMap(),
                arrayListOf("DeferredShadingRenderer")))
        scene.addChild(b)

        (0..(boundaryWidth * boundaryHeight * boundaryHeight).toInt()).map {
            val p = Node("Parent of $it")

            val inst = Mesh()
            inst.name = "Box_$it"
            inst.instanceOf = b
            inst.material = b.material

            inst.instancedProperties.put("ModelViewMatrix", { inst.modelView })
            inst.instancedProperties.put("ModelMatrix", { inst.model })
            inst.instancedProperties.put("MVP", { inst.mvp })

            val k: Double = it.rem(boundaryWidth)
            val j: Double = (it / boundaryWidth).rem(boundaryHeight)
            val i: Double = it / (boundaryWidth * boundaryHeight)

            p.position = GLVector(Math.floor(i).toFloat() * 3.0f, Math.floor(j).toFloat() * 3.0f, Math.floor(k).toFloat() * 3.0f)
            p.needsUpdate = true
            p.needsUpdateWorld = true
            p.addChild(inst)

            container.addChild(p)
            inst
        }

        scene.addChild(container)

        val lights = (0..20).map {
            PointLight()
        }.map {
            it.position = Numerics.randomVectorFromRange(3, -600.0f, 600.0f)
            it.emissionColor = Numerics.randomVectorFromRange(3, 0.1f, 0.9f)
            it.intensity = Numerics.randomFromRange(5.0f, 150.0f)
            it.linear = 0.1f
            it.quadratic = 0.8f

            scene.addChild(it)
            it
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

        var ticks: Int = 0

        thread {
            while (true) {
                lights.mapIndexed {
                    i, light ->
                    val phi = Math.PI * 2.0f * ticks / 1500.0f

                    light.position = GLVector(
                        i.toFloat() * 10 * Math.sin(phi).toFloat() + Math.exp(i.toDouble()/10.0).toFloat(),
                        i.toFloat()*5.0f - 100.0f,
                        i.toFloat() * 10 * Math.cos(phi).toFloat() + Math.exp(i.toDouble()/10.0).toFloat())

                }

                ticks++

                container.rotation.rotateByEuler(0.001f, 0.001f, 0.0f)
                container.needsUpdate = true

                Thread.sleep(10)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }

}
