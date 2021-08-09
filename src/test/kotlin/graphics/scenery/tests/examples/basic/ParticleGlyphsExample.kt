package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.primitives.ParticleGlyphs
import graphics.scenery.attribute.material.Material


/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ParticleGlyphsExample : SceneryBase("ParticleGlyphsExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val hull = Box(Vector3f(10.0f, 10.0f, 10.0f), insideNormals = true)
        hull.material {
            diffuse = Vector3f(0.2f, 0.2f, 0.2f)
            cullingMode = Material.CullingMode.Front
        }
        scene.addChild(hull)

        val particlePos = listOf(Vector3f(0.0f, 0.0f, 0.0f))
        val particleProps = listOf(Vector3f(1.0f, 0.0f, 0.0f))
        val particleGlyphs = ParticleGlyphs(particlePos, particleProps)
        particleGlyphs.name = "Particles?"
        scene.addChild(particleGlyphs)

        val light = PointLight(radius = 15.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        cam.spatial {
            position = Vector3f(0.0f, 0.0f, 15.0f)
        }
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
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


