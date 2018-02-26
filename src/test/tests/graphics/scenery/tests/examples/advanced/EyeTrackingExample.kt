package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.PupilEyeTracker
import graphics.scenery.utils.Numerics
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.math.floor

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class EyeTrackingExample: SceneryBase("Eye Tracking Example", windowWidth = 1280, windowHeight = 720) {
    val pupilTracker = PupilEyeTracker()
    val hmd = OpenVRHMD(seated = false)
    val referenceTarget = Icosphere(1.0f, 3)

    override fun init() {
        hub.add(SceneryElement.HMDInput, hmd)

        renderer = Renderer.createRenderer(hub, applicationName, scene,
            windowWidth, windowHeight, renderConfigFile = "DeferredShadingStereo.yml")
        hub.add(SceneryElement.Renderer, renderer!!)

        referenceTarget.material.roughness = 1.0f
        referenceTarget.material.metallic = 0.5f
        referenceTarget.material.diffuse = GLVector(0.0f, 1.0f, 0.0f)
        scene.addChild(referenceTarget)

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

        setupCalibration()
    }

    fun setupCalibration(keybinding: String = "C") {
        val startCalibration = object : ClickBehaviour {

            override fun click(x: Int, y: Int) {
                if(!pupilTracker.isCalibrated) {
                    pupilTracker.calibrate(generateReferenceData = true, calibrationTarget = referenceTarget)
                }
            }
        }

        inputHandler?.addBehaviour("start_calibration", startCalibration)
        inputHandler?.addKeyBinding("start_calibration", keybinding)
    }
}
