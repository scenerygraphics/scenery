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


        val particleNumber = 100000
        val particlePos = FloatBuffer.allocate(particleNumber * 3)
        val particleProp = FloatBuffer.allocate(particleNumber * 2)
        val particleColor = FloatBuffer.allocate(particleNumber * 3)
        repeat(particleNumber) {
            particlePos.put(Random.nextDouble(-10.0, 10.0).toFloat())
            particlePos.put(Random.nextDouble(-10.0, 10.0).toFloat())
            particlePos.put(Random.nextDouble(-10.0, 10.0).toFloat())

            particleProp.put(Random.nextDouble(0.01, 0.1).toFloat())
            particleProp.put(0.0f)

            particleColor.put(Random.nextDouble(0.1, 1.0).toFloat())
            particleColor.put(Random.nextDouble(0.1, 1.0).toFloat())
            particleColor.put(Random.nextDouble(0.1, 1.0).toFloat())
        }
        particlePos.flip()
        particleProp.flip()
        particleColor.flip()
        val particleGlyphs = ParticleGlyphs(particlePos, particleProp, particleColor, false)
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


        val velocity = 0.10
        thread {
            while(!scene.initialized) {
                Thread.sleep(200)
            }

            while (running) {

                val particleNewPos = FloatBuffer.allocate(particleNumber * 3)
                val particleNewColor = FloatBuffer.allocate(particleNumber * 3)
                val particleNewProp = FloatBuffer.allocate(particleNumber * 2)
                particlePos.position(0)
                particleColor.position(0)
                particleProp.position(0)
                repeat(particleNumber)
                {
                    particleNewPos.put(particlePos.get() + Random.nextDouble(-velocity, velocity).toFloat())
                    particleNewPos.put(particlePos.get() + Random.nextDouble(-velocity, velocity).toFloat())
                    particleNewPos.put(particlePos.get() + Random.nextDouble(-velocity, velocity).toFloat())

                    particleNewColor.put(particleColor.get() + Random.nextDouble(0.1, 0.2).toFloat())
                    particleNewColor.put(particleColor.get() + Random.nextDouble(0.1, 0.2).toFloat())
                    particleNewColor.put(particleColor.get() + Random.nextDouble(0.1, 0.2).toFloat())

                    particleNewProp.put(particleProp.get() + Random.nextDouble(-velocity, velocity).toFloat())
                    particleNewProp.put(0.0f)

                }
                particleNewPos.flip()
                particleNewColor.flip()
                particleNewProp.flip()

                particlePos.flip()
                particleColor.flip()
                particleProp.flip()
                particleGlyphs.updatePositions(particleNewPos)
                particleGlyphs.updateProperties(particleNewProp, particleNewColor)

                Thread.sleep(50)
            }
        }

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ParticleGlyphsExample().main()
        }
    }
}


