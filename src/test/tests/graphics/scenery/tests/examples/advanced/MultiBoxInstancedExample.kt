package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
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

        val boundaryWidth = 50.0
        val boundaryHeight = 50.0

        val container = Mesh()

        val b = Box(GLVector(0.7f, 0.7f, 0.7f))
        b.name = "boxmaster"
        b.instancedProperties.put("ModelMatrix", { b.model })
        b.material = ShaderMaterial.fromFiles("DefaultDeferredInstanced.vert", "DefaultDeferred.frag")
        b.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        b.material.ambient = GLVector(1.0f, 1.0f, 1.0f)
        b.material.specular = GLVector(1.0f, 1.0f, 1.0f)
        b.material.metallic = 0.0f
        b.material.roughness = 1.0f

        scene.addChild(b)

        (0 until (boundaryWidth * boundaryHeight * boundaryHeight).toInt()).map {
            val inst = Mesh()
            inst.name = "Box_$it"
            inst.material = b.material

            inst.instancedProperties["ModelMatrix"] = { inst.world }

            val k: Double = it.rem(boundaryWidth)
            val j: Double = (it / boundaryWidth).rem(boundaryHeight)
            val i: Double = it / (boundaryWidth * boundaryHeight)

            inst.position = GLVector(Math.floor(i).toFloat(), Math.floor(j).toFloat(), Math.floor(k).toFloat())

            b.instances.add(inst)
            inst.parent = container
            inst
        }

        val lights = (0..20).map {
            PointLight(radius = 450.0f)
        }.map {
            it.position = Random.randomVectorFromRange(3, -100.0f, 100.0f)
            it.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            it.intensity = Random.randomFromRange(0.1f, 5.0f)

            scene.addChild(it)
            it
        }

        val hullbox = Box(GLVector(100.0f, 100.0f, 100.0f))
        hullbox.position = GLVector(0.0f, 0.0f, 0.0f)
        hullbox.name = "hullbox"
        hullbox.material.ambient = GLVector(0.6f, 0.6f, 0.6f)
        hullbox.material.diffuse = GLVector(0.4f, 0.4f, 0.4f)
        hullbox.material.specular = GLVector(0.0f, 0.0f, 0.0f)
        hullbox.material.cullingMode = Material.CullingMode.Front

        scene.addChild(hullbox)

        thread {
            while(!sceneInitialized()) {
                Thread.sleep(200)
            }

            while (true) {
                container.rotation.rotateByEuler(0.001f, 0.001f, 0.0f)
                container.needsUpdateWorld = true
                container.needsUpdate = true
                container.updateWorld(true, false)

                val inst = Mesh()
                inst.instancedProperties["ModelMatrix"] = { inst.world }
                inst.position = Random.randomVectorFromRange(3, -40.0f, 40.0f)
                inst.parent = container
                b.instances.add(inst)
                b.instances.removeAt(kotlin.random.Random.nextInt(b.instances.size - 1))

                Thread.sleep(20)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }

}
