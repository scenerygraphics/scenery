package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.PupilEyeTracker
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.numerics.Random
import org.junit.Test
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class EyeTrackingExample: SceneryBase("Eye Tracking Example", windowWidth = 1280, windowHeight = 720) {
    val pupilTracker = PupilEyeTracker(calibrationType = PupilEyeTracker.CalibrationType.ScreenSpace)
    val hmd = OpenVRHMD(seated = false, useCompositor = true)
    val referenceTarget = Icosphere(0.005f, 2)

    override fun init() {
        hub.add(SceneryElement.HMDInput, hmd)

        renderer = Renderer.createRenderer(hub, applicationName, scene,
            windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)
        renderer?.toggleVR()

        val cam: DetachedHeadCamera = DetachedHeadCamera(hmd)
        with(cam) {
            position = GLVector(0.0f, 0.2f, 5.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat(), 0.05f, 100.0f)
            active = true

            scene.addChild(this)
        }
        cam.disableCulling = true

        referenceTarget.material.roughness = 1.0f
        referenceTarget.material.metallic = 0.5f
        referenceTarget.material.diffuse = GLVector(0.8f, 0.8f, 0.8f)
        scene.addChild(referenceTarget)

        val lightbox = Box(GLVector(25.0f, 25.0f, 25.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material.diffuse = GLVector(0.4f, 0.4f, 0.4f)
        lightbox.material.roughness = 1.0f
        lightbox.material.metallic = 0.0f
        lightbox.material.cullingMode = Material.CullingMode.Front

        scene.addChild(lightbox)

        (0..10).map {
            val light = PointLight(radius = 15.0f)
            light.emissionColor = Random.randomVectorFromRange(3, 0.0f, 1.0f)
            light.position = Random.randomVectorFromRange(3, -5.0f, 5.0f)
            light.intensity = 100.0f

            light
        }.forEach { scene.addChild(it) }

        thread {
            while(!running) {
                Thread.sleep(200)
            }

            hmd.events.onDeviceConnect.add { hmd, device, timestamp ->
                if(device.type == TrackedDeviceType.Controller) {
                    logger.info("Got device ${device.name} at $timestamp")
                    device.model?.let { hmd.attachToNode(device, it, cam) }
                }
            }
        }


        /*
        val calibrationTarget = Icosphere(0.1f, 2)
        calibrationTarget.material.diffuse = GLVector(0.8f, 0.7f, 0.7f)
        calibrationTarget.position = cam.position + cam.headPosition + GLVector(0.0f, 0.0f, -2.0f)
        scene.addChild(calibrationTarget)

        thread {
            while(!running) {
                Thread.sleep(200)
            }

            val originalPosition = calibrationTarget.position.clone()
            var ticks = 0
            while(running) {

                calibrationTarget.position = originalPosition + cam.viewportToWorld(GLVector(
                    2.0f * cos(ticks/200f) - 1.0f,
                    -2.0f * sin(ticks/200f) - 1.0f ), 0.5f
                )

                ticks++
                Thread.sleep(5)
            }
        }
        */
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
                        for(i in 0 until 2) {
                            referenceTarget.material.diffuse = GLVector(1.0f, 0.0f, 0.0f)
                            Thread.sleep(300)
                            referenceTarget.material.diffuse = GLVector(0.8f, 0.8f, 0.8f)
                            Thread.sleep(300)
                        }
                    }

                    pupilTracker.onCalibrationSuccess = {
                        for(i in 0 until 20) {
                            referenceTarget.material.diffuse = GLVector(0.0f, 1.0f, 0.0f)
                            Thread.sleep(100)
                            referenceTarget.material.diffuse = GLVector(0.8f, 0.8f, 0.8f)
                            Thread.sleep(30)
                        }
                    }

                    logger.info("Starting eye tracker calibration")
                    pupilTracker.calibrate(cam, hmd,
                        generateReferenceData = true,
                        calibrationTarget = referenceTarget)

                    pupilTracker.onGazeReceived = when (pupilTracker.calibrationType) {
                        PupilEyeTracker.CalibrationType.ScreenSpace -> { gaze ->
                            referenceTarget.position = cam.viewportToWorld(
                                GLVector(
                                    gaze.normalizedPosition().x() * 2.0f - 1.0f,
                                    gaze.normalizedPosition().y() * 2.0f - 1.0f),
                                offset = 0.5f) + cam.forward * 0.15f

                            when {
                                gaze.confidence < 0.85f -> referenceTarget.material.diffuse = GLVector(0.8f, 0.0f, 0.0f)
                                gaze.confidence > 0.85f -> referenceTarget.material.diffuse = GLVector(0.0f, 0.5f, 0.5f)
                                gaze.confidence > 0.95f -> referenceTarget.material.diffuse = GLVector(0.0f, 1.0f, 0.0f)
                            }
                        }

                        PupilEyeTracker.CalibrationType.WorldSpace -> { gaze ->
                            referenceTarget.position = gaze.gazePoint()

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
