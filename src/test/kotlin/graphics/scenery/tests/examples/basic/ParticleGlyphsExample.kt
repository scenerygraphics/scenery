package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material

import graphics.scenery.primitives.ParticleGlyphs

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

        val particleNumber = 500000
        val particlePositions = ArrayList<Vector3f>(particleNumber)
        val particleProperties = ArrayList<Vector3f>(particleNumber)
        repeat(particleNumber) {
            particlePositions.add(graphics.scenery.numerics.Random.random3DVectorFromRange(-50.0f, 50.0f))
            particleProperties.add(Vector3f(Random.nextDouble(0.1, 1.0).toFloat(), Random.nextDouble(0.0, 1.0).toFloat(), 0.0f))
        }
        val particleGlyphs = ParticleGlyphs(particlePositions, particleProperties, false)
        particleGlyphs.name = "Particles?"
        scene.addChild(particleGlyphs)

        val camStartDist = 50.0f
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


