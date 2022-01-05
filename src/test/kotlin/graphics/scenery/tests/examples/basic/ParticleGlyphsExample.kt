package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material

import graphics.scenery.primitives.ParticleGlyphs
import graphics.scenery.utils.Statistics
import org.joml.Vector2f
import java.nio.FloatBuffer
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

import kotlin.random.Random

class ParticleGlyphsExample : SceneryBase("ParticleGlyphsExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val hull = Box(Vector3f(250.0f, 250.0f, 250.0f), insideNormals = true)
        hull.material {
            diffuse = Vector3f(0.07f, 0.07f, 0.07f)
            cullingMode = Material.CullingMode.Front
        }
        scene.addChild(hull)

        val light1 = PointLight(radius = 200.0f)
        light1.spatial().position = Vector3f(0.0f, 0.0f, 0.0f)
        light1.intensity = 5.0f
        light1.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light1)

        val light2 = PointLight(radius = 200.0f)
        light2.spatial().position = Vector3f(100.0f, 100.0f, 100.0f)
        light2.intensity = 5.0f
        light2.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light2)

        val light3 = PointLight(radius = 200.0f)
        light3.spatial().position = Vector3f(0.0f, 0.0f, -100.0f)
        light3.intensity = 5.0f
        light3.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light3)


        val particleNumber = 1000
        val particlePos = FloatBuffer.allocate(particleNumber * 3)
        val particleProp = FloatBuffer.allocate(particleNumber * 2)
        val particleCol = FloatBuffer.allocate(particleNumber * 3)
        repeat(particleNumber) {
            particlePos.put(Random.nextDouble(-10.0, 10.0).toFloat())
            particlePos.put(Random.nextDouble(-10.0, 10.0).toFloat())
            particlePos.put(Random.nextDouble(-10.0, 10.0).toFloat())

            particleProp.put(Random.nextDouble(0.01, 0.1).toFloat())
            particleProp.put(0.0f)

            particleCol.put(Random.nextDouble(0.1, 1.0).toFloat())
            particleCol.put(Random.nextDouble(0.1, 1.0).toFloat())
            particleCol.put(Random.nextDouble(0.1, 1.0).toFloat())
        }
        particlePos.flip()
        particleProp.flip()
        particleCol.flip()
        val particleGlyphs = ParticleGlyphs(particlePos, particleProp, particleCol, false)
        particleGlyphs.name = "Particles"
        scene.addChild(particleGlyphs)



        var camStartDist = 10.0f
        val camStart = Vector3f(0.0f, 0.0f, camStartDist)
        val cam: Camera = DetachedHeadCamera()
        cam.spatial {
            position = camStart
        }
        cam.perspectiveCamera(60.0f, windowWidth, windowHeight)
        cam.target = Vector3f(0.0f, 0.0f, 0.0f)
        scene.addChild(cam)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ParticleGlyphsExample().main()
        }
    }
}


