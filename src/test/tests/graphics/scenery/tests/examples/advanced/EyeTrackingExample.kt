package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.PupilEyeTracker
import graphics.scenery.utils.Numerics
import org.junit.Test
import kotlin.math.floor

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class EyeTrackingExample: SceneryBase("Eye Tracking Example", windowWidth = 1280, windowHeight = 720) {
    val pupilTracker = PupilEyeTracker()

    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        pupilTracker.calibrate(generateReferenceData = true)

        val s = Icosphere(1.0f, 3)
        s.material.roughness = 1.0f
        s.material.metallic = 0.5f
        s.material.diffuse = GLVector(0.0f, 1.0f, 0.0f)
        scene.addChild(s)

        val stageLight = PointLight(radius = 35.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 100.0f
        stageLight.position = GLVector(0.0f, 0.0f, -5.0f)
        scene.addChild(stageLight)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.2f, 12.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true

            scene.addChild(this)
        }
    }

    @Test override fun main() {
        super.main()
    }
}
