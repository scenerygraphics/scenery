package graphics.scenery.tests.examples.basic

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.behaviours.MouseDragSphere
import graphics.scenery.controls.behaviours.SelectCommand
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Atmosphere
import org.joml.Vector3f
import kotlin.concurrent.thread

/**
 * <Description>
 * A basic example that shows how the atmosphere object can be applied to a scene.
 *
 * @author Samuel Pantze
 */

//@ShaderProperty
//var sunPos = Vector3f(0f, 0.5f, -1f)

class AtmosphereExample : SceneryBase("Atmosphere Example",
    windowWidth = 1024, windowHeight = 768) {

    /** Whether to run this example in VR mode. */
    private val useVR = false

    private val atmos = Atmosphere()
    private lateinit var hmd: OpenVRHMD

    override fun init() {

        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        if (useVR) {
            hmd = OpenVRHMD(useCompositor = true)
            hub.add(SceneryElement.HMDInput, hmd)
            renderer?.toggleVR()
        }

        val ball = Icosphere(0.5f, 2)
        ball.material {
            diffuse = Vector3f(1f, 1f, 1f)
            roughness = 0.5f
        }
        scene.addChild((ball))

        val ambientLight = AmbientLight(0.1f)
        scene.addChild(ambientLight)

        //val sunlight = DirectionalLight(Vector3f(0f, 0.5f, -1f))
        //sunlight.intensity = 2f
        //scene.addChild(sunlight)

        val lights = (1 until 5).map {
            val light = PointLight(10f)
            val spread = 2f
            light.spatial().position = Vector3f(
                Random.randomFromRange(-spread, spread),
                Random.randomFromRange(-spread, spread),
                Random.randomFromRange(-spread, spread),
            )
            light.intensity = 1f
            //light.emissionColor = Vector3f(1f, 0.9f, 0.8f)
            scene.addChild(light)
            light
        }

        atmos.attachBehaviors(inputHandler, scene)
        scene.addChild(atmos)

        val cam: Camera = if (useVR) {DetachedHeadCamera(hmd)} else {DetachedHeadCamera()}
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(70.0f, 512, 768)
            scene.addChild(this)
        }
    }

    override fun inputSetup() {
        super.inputSetup()

        setupCameraModeSwitching()

        //val moveSun: (Scene.RaycastResult, Int, Int) -> Unit = { result, x, y ->
        //    result.initialDirection .let {
        //        atmos.sunPos = it.normalize()
        //    }
        //}
//
        //renderer?.let { r ->
        //    inputHandler?.addBehaviour(
        //        "clickSun", SelectCommand(
        //            "clickSun", r, scene,
        //            { scene.findObserver() }, action = moveSun, debugRaycast = false
        //        )
        //    )
        //    inputHandler?.addKeyBinding("clickSun", "double-click button1")
        //}

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            AtmosphereExample().main()
        }
    }
}
