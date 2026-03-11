package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material

import graphics.scenery.primitives.ParticleGlyphs
import graphics.scenery.utils.Statistics
import graphics.scenery.utils.VideoEncodingQuality
import graphics.scenery.utils.extensions.plus
import org.joml.Vector2f
import java.io.File
import java.nio.FloatBuffer
import kotlin.concurrent.thread
import kotlin.math.*

import kotlin.random.Random
import kotlin.test.assertTrue

class ParticleGlyphsExample : SceneryBase("ParticleGlyphsExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val hull = Box(Vector3f(50.0f, 50.0f, 50.0f), insideNormals = true)
        hull.material {
            diffuse = Vector3f(0.07f, 0.07f, 0.07f)
            cullingMode = Material.CullingMode.Front
        }
        scene.addChild(hull)

        val light1 = PointLight(radius = 50.0f)
        light1.spatial().position = Vector3f(10.0f, 10.0f, 10.0f)
        light1.intensity = 5.0f
        light1.emissionColor = Vector3f(0.8f, 0.8f, 0.8f)
        //scene.addChild(light1)

        val light2 = PointLight(radius = 50.0f)
        light2.spatial().position = Vector3f(-10.0f, -10.0f, -10.0f)
        light2.intensity = 5.0f
        light2.emissionColor = Vector3f(0.8f, 0.8f, 0.8f)
        //scene.addChild(light2)

        val particleNumber = 200000
        val particlePos = FloatBuffer.allocate(particleNumber * 3)
        val particleProp = FloatBuffer.allocate(particleNumber * 2)
        val particleColor = FloatBuffer.allocate(particleNumber * 3)

        val particleDir = FloatBuffer.allocate(particleNumber * 3)
        val particleVelocity = FloatBuffer.allocate(particleNumber)
        repeat(particleNumber) {
            particlePos.put(Random.nextDouble(-0.01, 0.01).toFloat())
            particlePos.put(Random.nextDouble(-0.01, 0.01).toFloat())
            particlePos.put(Random.nextDouble(-0.01, 0.01).toFloat())

            particleProp.put(Random.nextDouble(0.01, 0.05).toFloat())
            particleProp.put(0.0f)

            val xDir = Random.nextDouble(-1.0, 1.0).toFloat()
            val yDir = Random.nextDouble(-1.0, 1.0).toFloat()
            val zDir = Random.nextDouble(-1.0, 1.0).toFloat()
            particleDir.put(xDir)
            particleDir.put(yDir)
            particleDir.put(zDir)

            val velocity = Random.nextDouble(0.0, 0.01).toFloat()
            particleVelocity.put(velocity)

            particleColor.put(abs(xDir) * (velocity * 10.0f))
            particleColor.put(abs(yDir) * (velocity * 10.0f))
            particleColor.put(abs(zDir) * (velocity * 10.0f))
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
        cam.targeted = true
        cam.target = Vector3f(0.0f, 0.0f, 0.0f)
        scene.addChild(cam)

        val light3 = PointLight(radius = 50.0f)
        light3.spatial().position = camStart + Vector3f(0.0f, 1.0f, 0.0f)
        light3.intensity = 16.0f
        light3.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light3)

        thread {
            while(!scene.initialized) {
                Thread.sleep(200)
            }
            Thread.sleep(1000)
            while (running) {
                val particleNewPos = FloatBuffer.allocate(particleNumber * 3)
                particlePos.position(0)

                particleDir.position(0)
                particleVelocity.position(0)

                repeat(particleNumber)
                {
                    val velocity = particleVelocity.get()
                    particleNewPos.put(particlePos.get() + particleDir.get() * velocity)
                    particleNewPos.put(particlePos.get() + particleDir.get() * velocity)
                    particleNewPos.put(particlePos.get() + particleDir.get() * velocity)
                }
                particleNewPos.flip()
                particlePos.flip()
                particlePos.put(particleNewPos)
                particlePos.flip()
                particleGlyphs.updatePositions(particlePos)

                Thread.sleep(50)
            }
        }

        var camPos = camStart
        val velocityFactor = 0.01f
        val velocity = 0.25f
        var amount = 0.0f

        /*thread {
            while(!scene.initialized) {
                Thread.sleep(200)
            }
            Thread.sleep(1000)

            while(true) {
                amount += velocity
                camPos.x = sin(amount * velocityFactor) * camStartDist
                camPos.z = cos(amount * velocityFactor) * camStartDist
                cam.spatial {
                    rotation.rotateY(-velocityFactor * 2.5f / 10.0f)
                    position = camPos
                }
                light3.spatial()
                {
                    position = (cam.spatial().position) + Vector3f(0.0f, 1.0f, 0.0f)
                }
                Thread.sleep(20)
            }
        }

        thread {
            while(renderer?.firstImageReady == false) {
                Thread.sleep(50)
            }
            settings.set("VideoEncoder.Quality", "High")
            Thread.sleep(6000)
            renderer?.recordMovie("./ParticleGlyphsExample.mp4")
            Thread.sleep(15000)
            renderer?.recordMovie()
        }*/
    }

    override fun main() {
        // add assertions, these only get called when the example is called
        // as part of scenery's integration tests
        assertions[AssertionCheckPoint.AfterClose]?.add {
            val f = File("./ParticleGlyphsExample.mp4")
            try {
                assertTrue(f.length() > 0, "Size of recorded video is larger than zero.")
            } finally {
                if(f.exists()) {
                    f.delete()
                }
            }
        }

        super.main()
    }
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ParticleGlyphsExample().main()
        }
    }
}


