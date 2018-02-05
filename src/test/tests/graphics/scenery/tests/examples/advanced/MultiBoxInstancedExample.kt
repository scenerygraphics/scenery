package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.Numerics
import org.junit.Test
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
            position = GLVector(10.0f, 10.0f, 10.0f)
            perspectiveCamera(60.0f, 1.0f * windowWidth, 1.0f * windowHeight, 1.0f, 1000.0f)
            active = true

            scene.addChild(this)
        }

        val boundaryWidth = 100.0
        val boundaryHeight = 50.0

        val container = Mesh()

        val b = Box(GLVector(0.2f, 0.2f, 0.2f))
        b.material = Material()
        b.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        b.material.ambient = GLVector(1.0f, 1.0f, 1.0f)
        b.material.specular = GLVector(1.0f, 1.0f, 1.0f)
        b.name = "boxmaster"

        b.instanceMaster = true
        b.instancedProperties.put("ModelMatrix", { b.model })
        b.material = ShaderMaterial(arrayListOf("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"))
        scene.addChild(b)

        (0 until (boundaryWidth * boundaryHeight * boundaryHeight).toInt()).map {
            val inst = Mesh()
            inst.name = "Box_$it"
            inst.instanceOf = b
            inst.material = b.material

            inst.instancedProperties["ModelMatrix"] = { inst.world }

            val k: Double = it.rem(boundaryWidth)
            val j: Double = (it / boundaryWidth).rem(boundaryHeight)
            val i: Double = it / (boundaryWidth * boundaryHeight)

            val jitter = Numerics.randomVectorFromRange(3, -0.1f, 0.1f)

            inst.position = GLVector(Math.floor(i).toFloat(), Math.floor(j).toFloat(), Math.floor(k).toFloat()) + jitter
            inst.needsUpdate = true
            inst.needsUpdateWorld = true

            b.instances.add(inst)
            inst.parent = container
            inst
        }

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
        hullbox.name = "hullbox"
        hullbox.material.ambient = GLVector(0.6f, 0.6f, 0.6f)
        hullbox.material.diffuse = GLVector(0.4f, 0.4f, 0.4f)
        hullbox.material.specular = GLVector(0.0f, 0.0f, 0.0f)
        hullbox.material.doubleSided = true

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

                container.rotation.rotateByAngleY(0.001f)
                container.needsUpdateWorld = true
                container.needsUpdate = true
                container.updateWorld(true, false)

                Thread.sleep(10)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }

}
