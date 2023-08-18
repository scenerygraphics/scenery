package graphics.scenery.tests.examples.basic

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.behaviours.SelectCommand
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Atmosphere
import org.joml.Quaternionfc
import org.joml.Vector3f
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * <Description>
 * A basic example that shows how the atmosphere shader can be applied to a scene.
 *
 * @author Samuel Pantze
 */

//@ShaderProperty
//var sunPos = Vector3f(0f, 0.5f, -1f)

class AtmosphereExample : SceneryBase("Atmosphere Example") {

    private val atmos = Atmosphere()

    override fun init() {
        renderer = hub.add(
            SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val ball = Icosphere(0.5f, 2)
        ball.material {
            diffuse = Vector3f(1f, 1f, 1f)
            roughness = 0.5f
        }
        scene.addChild((ball))

        val lights = (1 until 5).map {
            val light = PointLight(20f)
            val spread = 2f
            light.spatial().position = Vector3f(
                Random.randomFromRange(-spread, spread),
                Random.randomFromRange(-spread, spread),
                Random.randomFromRange(-spread, spread),
            )
            light.intensity = 1f
            light.emissionColor = Vector3f(1f, 0.9f, 0.8f)
            scene.addChild(light)
            light
        }

        scene.addChild(atmos)

        //thread {
        //    var ticks = 0L
        //    while (true) {
        //        val x = (cos(ticks / 100f))
        //        val z = (sin(ticks / 100f))
        //        atmos.sunPos = Vector3f(
        //            1f,
        //            1f,
        //            1f
        //        )
        //        ticks++
        //        Thread.sleep(20)
        //    }
        //}

        val cam: Camera = DetachedHeadCamera()
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

        val moveSun: (Scene.RaycastResult, Int, Int) -> Unit = { result, x, y ->
            result.initialDirection .let {
                atmos.sunPos = it.normalize()
            }
        }

        renderer?.let { r ->
            inputHandler?.addBehaviour(
                "moveSun", SelectCommand(
                    "moveSun", r, scene,
                    { scene.findObserver() }, action = moveSun, debugRaycast = false
                )
            )
        }
        inputHandler?.addKeyBinding("moveSun", "ctrl button2")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            AtmosphereExample().main()
        }
    }
}
