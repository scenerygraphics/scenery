package graphics.scenery.tests.examples.advanced

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.eyetracking.PupilEyeTracker
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class EyeTrackingExample: SceneryBase("Eye Tracking Example", windowWidth = 1280, windowHeight = 720) {
    val pupilTracker = PupilEyeTracker()
    val hmd = OpenVRHMD(seated = false, useCompositor = true)
    val referenceTarget = Icosphere(0.004f, 2)
    val calibrationTarget = Icosphere(0.02f, 2)
    val confidenceThreshold = 0.65f

    override fun init() {
        hub.add(SceneryElement.HMDInput, hmd)

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene,
            windowWidth, windowHeight))
        renderer?.toggleVR()

        val cam = DetachedHeadCamera(hmd)
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.2f, 5.0f)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight, 0.05f, 100.0f)

            scene.addChild(this)
        }
        cam.disableCulling = true

        referenceTarget.visible = false
        referenceTarget.ifMaterial {
            roughness = 1.0f
            metallic = 0.5f
            diffuse = Vector3f(0.8f, 0.8f, 0.8f)
        }
        cam.addChild(referenceTarget)

        calibrationTarget.visible = false
        referenceTarget.ifMaterial {
            roughness = 1.0f
            metallic = 0.5f
            diffuse = Vector3f(0.8f, 0.8f, 0.8f)
        }
        cam.addChild(calibrationTarget)


        val lightbox = Box(Vector3f(20.0f, 20.0f, 20.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material {
            diffuse = Vector3f(0.4f, 0.4f, 0.4f)
            roughness = 1.0f
            metallic = 0.0f
            cullingMode = Material.CullingMode.Front
        }

        scene.addChild(lightbox)

        (0..10).map {
            val light = PointLight(radius = 15.0f)
            light.emissionColor = Random.random3DVectorFromRange(0.0f, 1.0f)
            light.spatial {
                position = Random.random3DVectorFromRange(-5.0f, 5.0f)
            }
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

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            EyeTrackingExample().main()
        }
    }

    private fun setupCalibration() {
        val startCalibration = ClickBehaviour { _, _ ->
            thread {
                val cam = scene.findObserver()
                if (!pupilTracker.isCalibrated && cam != null) {
                    pupilTracker.onCalibrationFailed = {
                        for(i in 0 until 2) {
                            referenceTarget.ifMaterial {
                                diffuse = Vector3f(1.0f, 0.0f, 0.0f)
                                Thread.sleep(300)
                                diffuse = Vector3f(0.8f, 0.8f, 0.8f)
                                Thread.sleep(300)
                            }
                        }
                    }

                    pupilTracker.onCalibrationSuccess = {
                        for(i in 0 until 20) {
                            referenceTarget.ifMaterial {
                                diffuse = Vector3f(0.0f, 1.0f, 0.0f)
                                Thread.sleep(100)
                                diffuse = Vector3f(0.8f, 0.8f, 0.8f)
                                Thread.sleep(30)
                            }
                        }
                    }

                    logger.info("Starting eye tracker calibration")
                    pupilTracker.calibrate(cam, hmd,
                        generateReferenceData = true,
                        calibrationTarget = calibrationTarget)

                    pupilTracker.onGazeReceived = when (pupilTracker.calibrationType) {


                        PupilEyeTracker.CalibrationType.WorldSpace -> { gaze ->
                            when {
                                gaze.confidence < confidenceThreshold -> referenceTarget.ifMaterial { diffuse = Vector3f(0.8f, 0.0f, 0.0f) }
                                gaze.confidence > confidenceThreshold -> referenceTarget.ifMaterial { diffuse = Vector3f(0.0f, 0.5f, 0.5f) }
                                gaze.confidence > 0.95f -> referenceTarget.ifMaterial { diffuse = Vector3f(0.0f, 1.0f, 0.0f) }
                            }
                            if(gaze.confidence > confidenceThreshold) {
                                val p = Vector3f(gaze.gazeDirection().x* gaze.gazeDistance(),gaze.gazeDirection().y* gaze.gazeDistance(),gaze.gazeDirection().z* gaze.gazeDistance())
                                referenceTarget.ifSpatial {
                                    position = p
                                }
                                referenceTarget.visible = true
                            }
                        }
                    }
                }
            }

            logger.info("Calibration routine done.")
        }

        // bind calibration start to menu key on controller
        hmd.addBehaviour("start_calibration", startCalibration)
        hmd.addKeyBinding("start_calibration", "M")
    }
}
