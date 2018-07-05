package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.PupilEyeTracker
import graphics.scenery.controls.TrackedDeviceType
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class EyeTrackingExample: SceneryBase("Eye Tracking Example", windowWidth = 1280, windowHeight = 720) {
    val pupilTracker = PupilEyeTracker(calibrationType = PupilEyeTracker.CalibrationType.ScreenSpace)
    val hmd = OpenVRHMD(seated = false, useCompositor = true)
    val referenceTarget = Icosphere(0.005f, 3)

    override fun init() {
        hub.add(SceneryElement.HMDInput, hmd)

        renderer = Renderer.createRenderer(hub, applicationName, scene,
            windowWidth, windowHeight, renderConfigFile = "DeferredShadingStereo.yml")
        hub.add(SceneryElement.Renderer, renderer!!)

        settings.set("vr.Active", true)
        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = GLVector(0.0f, 0.2f, 5.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true

            scene.addChild(this)
        }

        referenceTarget.material.roughness = 1.0f
        referenceTarget.material.metallic = 0.5f
        referenceTarget.material.diffuse = GLVector(0.8f, 0.8f, 0.8f)
        scene.addChild(referenceTarget)

        val lightbox = Box(GLVector(25.0f, 25.0f, 25.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        lightbox.material.roughness = 1.0f
        lightbox.material.metallic = 0.0f
        lightbox.material.cullingMode = Material.CullingMode.Front

        scene.addChild(lightbox)
        val stageLight = PointLight(radius = 35.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 100.0f
        stageLight.position = GLVector(0.0f, 4.0f, 4.0f)
        scene.addChild(stageLight)

        thread {
            while(!running) {
                Thread.sleep(200)
            }

            hmd.getTrackedDevices(TrackedDeviceType.Controller).forEach { _, device ->
                val c = Mesh()
                c.name = device.name
                hmd.loadModelForMesh(device, c)
                hmd.attachToNode(device, c, cam)
            }
        }
    }

    override fun inputSetup() {
        super.inputSetup()

        inputHandler?.let { handler ->
            hashMapOf(
                "move_forward_fast" to "W",
                "move_back_fast" to "S",
                "move_left_fast" to "A",
                "move_right_fast" to "D").forEach { name, key ->
                handler.getBehaviour(name)?.let { b ->
                    hmd.addBehaviour(name, b)
                    hmd.addKeyBinding(name, key)
                }
            }
        }
        setupCalibration()
    }

    @Test override fun main() {
        super.main()
    }

    private fun setupCalibration() {
        val startCalibration = ClickBehaviour { _, _ ->
            thread {
                val cam = scene.findObserver()
                if (!pupilTracker.isCalibrated && cam != null) {
                    pupilTracker.onCalibrationFailed = {
                        for(i in (0..3)) {
                            referenceTarget.material.diffuse = GLVector(0.0f, 1.0f, 0.0f)
                            Thread.sleep(200)
                            referenceTarget.material.diffuse = GLVector(0.8f, 0.8f, 0.8f)
                        }
                    }

                    pupilTracker.onCalibrationSuccess = {
                        for(i in (0..3)) {
                            referenceTarget.material.diffuse = GLVector(0.0f, 1.0f, 0.0f)
                            Thread.sleep(200)
                            referenceTarget.material.diffuse = GLVector(0.8f, 0.8f, 0.8f)
                        }
                    }

                    logger.info("Starting eye tracker calibration")
                    pupilTracker.calibrate(cam, hmd,
                        generateReferenceData = true,
                        calibrationTarget = referenceTarget)

                    pupilTracker.onGazeReceived = when (pupilTracker.calibrationType) {
                        PupilEyeTracker.CalibrationType.ScreenSpace -> { gaze ->
                            referenceTarget.position = cam.viewportToWorld(GLVector(gaze.normalizedPosition().x() * 2.0f - 1.0f, gaze.normalizedPosition().y() * 2.0f - 1.0f))

                            when {
                                gaze.confidence < 0.85f -> referenceTarget.material.diffuse = GLVector(0.8f, 0.0f, 0.0f)
                                gaze.confidence > 0.85f -> referenceTarget.material.diffuse = GLVector(0.0f, 0.5f, 0.5f)
                                gaze.confidence > 0.95f -> referenceTarget.material.diffuse = GLVector(0.0f, 1.0f, 0.0f)
                            }
                        }

                        PupilEyeTracker.CalibrationType.WorldSpace -> { gaze ->
                            referenceTarget.position = gaze.gazePoint() ?: GLVector.getNullVector(3)

                            when {
                                gaze.confidence < 0.85f -> referenceTarget.material.diffuse = GLVector(0.8f, 0.0f, 0.0f)
                                gaze.confidence > 0.85f -> referenceTarget.material.diffuse = GLVector(0.0f, 0.5f, 0.5f)
                                gaze.confidence > 0.95f -> referenceTarget.material.diffuse = GLVector(0.0f, 1.0f, 0.0f)
                            }
                        }
                    }
                }
            }
        }

        hmd.addBehaviour("start_calibration", startCalibration)
        hmd.addKeyBinding("start_calibration", "M")
    }
}
