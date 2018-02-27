package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.PupilEyeTracker
import graphics.scenery.utils.Numerics
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread
import kotlin.math.floor

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class EyeTrackingExample: SceneryBase("Eye Tracking Example", windowWidth = 1280, windowHeight = 720) {
    val pupilTracker = PupilEyeTracker()
    val hmd = OpenVRHMD(seated = false, useCompositor = true)
    val referenceTarget = Icosphere(1.0f, 3)

    override fun init() {
        hub.add(SceneryElement.HMDInput, hmd)

        renderer = Renderer.createRenderer(hub, applicationName, scene,
            windowWidth, windowHeight, renderConfigFile = "DeferredShadingStereo.yml")
        hub.add(SceneryElement.Renderer, renderer!!)

        settings.set("vr.Active", true)
        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = GLVector(0.0f, 0.2f, 15.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true

            scene.addChild(this)
        }

        referenceTarget.material.roughness = 1.0f
        referenceTarget.material.metallic = 0.5f
        referenceTarget.material.diffuse = GLVector(0.8f, 0.0f, 0.0f)
        scene.addChild(referenceTarget)

        val lightbox = Box(GLVector(25.0f, 25.0f, 25.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        lightbox.material.roughness = 1.0f
        lightbox.material.metallic = 0.0f
        lightbox.material.doubleSided = true
        lightbox.material.cullingMode = Material.CullingMode.Front

        scene.addChild(lightbox)
        val stageLight = PointLight(radius = 35.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 100.0f
        stageLight.position = GLVector(0.0f, 0.0f, -5.0f)
        scene.addChild(stageLight)

    }

    override fun inputSetup() {
        super.inputSetup()
        setupCalibration()
    }

    @Test override fun main() {
        super.main()
    }

    private fun setupCalibration(keybinding: String = "C") {
        val startCalibration = ClickBehaviour { _, _ ->
            thread {
                if (!pupilTracker.isCalibrated) {
                    logger.info("Starting eye tracker calibration")
                    pupilTracker.calibrate(generateReferenceData = true, calibrationTarget = referenceTarget)
                }

                while(true) {
                    pupilTracker.currentGaze?.let { gaze ->
                        if(gaze.gazePoint().dimension == 3) {
                            referenceTarget.position = gaze.gazePoint()
                            logger.info("Current pos: ${referenceTarget.position}")
                        }
                    }

                    Thread.sleep(10)
                }
            }
        }

        inputHandler?.addBehaviour("start_calibration", startCalibration)
        inputHandler?.addKeyBinding("start_calibration", keybinding)
    }
}
