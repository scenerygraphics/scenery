package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.Numerics
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Demo animating multiple boxes with instancing.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class MultiBoxInstancedExample : SceneryBase("MultiBoxInstancedExample") {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(60.0f, 1.0f * windowWidth, 1.0f * windowHeight, 1.0f, 1000.0f)
            active = true

            scene.addChild(this)
        }

        val boundaryWidth = 50.0
        val boundaryHeight = 50.0

        val container = Mesh()

        val b = Box(GLVector(0.1f, 0.1f, 0.1f), insideNormals = true)
        b.material = Material()
        b.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        b.material.ambient = GLVector(1.0f, 1.0f, 1.0f)
        b.material.specular = GLVector(0.0f, 0.0f, 0.0f)
        b.name = "boxmaster"

        b.instanceMaster = true
        b.instancedProperties.put("ModelMatrix", { b.world })
        b.material = ShaderMaterial(arrayListOf("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"))
        scene.addChild(b)

        val instances = (0..(boundaryWidth * boundaryHeight * boundaryHeight).toInt()).map {
            val inst = Mesh()
            inst.name = "Box_$it"
            inst.instanceOf = b

            inst.instancedProperties.put("ModelMatrix", { inst.world })

            val i: Double = it.rem(boundaryWidth)
            val j: Double = it / boundaryWidth

            inst.position = GLVector(Math.floor(i).toFloat(), 0.0f, -Math.floor(j).toFloat())*0.1f
            inst.needsUpdate = true
            inst.needsUpdateWorld = true

            container.addChild(inst)
            container.discoveryBarrier = true
            inst
        }

        scene.addChild(container)

        val lights = (0..20).map {
            PointLight()
        }.map {
            it.position = Numerics.randomVectorFromRange(3, -50.0f, 50.0f)
            it.emissionColor = Numerics.randomVectorFromRange(3, 0.1f, 0.9f)
            it.intensity = Numerics.randomFromRange(20.0f, 150.0f)
            it.linear = 0.0f
            it.quadratic = 0.08f

            scene.addChild(it)
            it
        }

        var ticks = 0

        launch {
            while (true) {
                instances.map {
                    it.position.set(1, (Math.sin(1.0 * it.position.x()+ticks/100.0) * Math.cos(1.0 * it.position.z()+ticks/100.0)).toFloat())
                }

                ticks++

                container.updateWorld(true, true)

                delay(10, TimeUnit.MILLISECONDS)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }

}
