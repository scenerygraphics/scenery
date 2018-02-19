package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.Numerics
import org.junit.Test

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class PBLExample: SceneryBase("PBLExample", windowWidth = 1280, windowHeight = 720) {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val spheres = (0 until 16).map {
            val s = Sphere(0.5f, 40)
            s.position = GLVector(it / 4f, (it % 4).toFloat(), -3.0f) - GLVector(2.0f, 2.0f, 0.0f)
            s.material.roughness = (it / 4f)/4.0f
            s.material.metallic = (it % 4f)/4.0f

            s
        }

        spheres.forEach { scene.addChild(it) }

        val lights = (0 until 8).map {
            val l = PointLight(radius = 10.0f)
            l.position = GLVector(it / 4f, (it % 4).toFloat(), 2.0f) - GLVector(1.5f, 1.5f, 0.0f)
            l.emissionColor = Numerics.randomVectorFromRange(3, 0.2f, 0.8f)
            l.intensity = 10.0f
            l
        }

        lights.forEach { scene.addChild(it) }

        val stageLight = PointLight(radius = 15.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 100.0f
        scene.addChild(stageLight)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 0.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true

            scene.addChild(this)
        }
    }

    @Test override fun main() {
        super.main()
    }
}
