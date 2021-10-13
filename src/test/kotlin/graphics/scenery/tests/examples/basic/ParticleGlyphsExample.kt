package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.attribute.material.Material
import kotlin.concurrent.thread


import graphics.scenery.primitives.ParticleGlyphs


import kotlin.random.Random

class ParticleGlyphsExample : SceneryBase("ParticleGlyphsExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val hull = Box(Vector3f(200.0f, 200.0f, 200.0f), insideNormals = true)
        hull.material {
            diffuse = Vector3f(0.07f, 0.07f, 0.07f)
            cullingMode = Material.CullingMode.Front
        }
        scene.addChild(hull)

        val light0 = PointLight(radius = 400.0f)
        light0.spatial().position = Vector3f(0.0f, 5.0f, 3.0f)
        light0.intensity = 5.0f
        light0.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light0)

        val cam: Camera = DetachedHeadCamera()
        cam.spatial {
            position = Vector3f(0.0f, 0.0f, 10.0f)
        }
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        cam.target = Vector3f(150.0f, 0.0f, 150.0f)
        scene.addChild(cam)


        val particlePositions = mutableListOf<Vector3f>()
        val particleProperties = mutableListOf<Vector3f>()
        for(i in 0..1000000) {
            particlePositions.add(graphics.scenery.numerics.Random.random3DVectorFromRange(-30.0f, 30.0f))
            particleProperties.add(Vector3f(Random.nextDouble(0.0, 0.5).toFloat(), Random.nextDouble(0.0, 1.0).toFloat(), 0.0f))
        }
        val particleGlyphs = ParticleGlyphs(particlePositions, particleProperties, false)
        particleGlyphs.name = "Particles?"
        scene.addChild(particleGlyphs)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ParticleGlyphsExample().main()
        }
    }
}


