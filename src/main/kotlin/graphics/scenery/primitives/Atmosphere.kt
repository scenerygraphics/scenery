package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.InputHandler
import org.joml.Vector3f
import org.joml.Vector4f
import org.scijava.ui.behaviour.ClickBehaviour
import java.lang.Math.random
import java.lang.Math.toRadians
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.thread
import kotlin.math.*

/**
 * Implementation of a Nishita sky shader, applied to an [Icosphere] that wraps around the scene as a skybox.
 * The shader code is ported from Rye Terrells [repository](https://github.com/wwwtyro/glsl-atmosphere).
 * @param initSunDir [Vector3f] of the sun position. Defaults to sun elevation of the current local time.
 * @param emissionStrength Emission strength of the atmosphere shader. Defaults to 0.3f.
 * @param latitude Latitude of the user; needed to calculate the local sun position. Defaults to 15.0, which is central Germany.
 */
open class Atmosphere(initSunDir: Vector3f? = null, emissionStrength: Float = 0.3f, var latitude: Double = 15.0) :
    Icosphere(10f, 2, insideNormals = true) {

    @ShaderProperty
    var sunDir: Vector3f

    val sunProxy = Icosphere(1f, 2)

    var sunDirEnabledManual: Boolean = false

    //val sunLight = DirectionalLight(sunPos)

    init {
        this.name = "Atmosphere"
        setMaterial(ShaderMaterial.fromClass(this::class.java))
        material {
            cullingMode = Material.CullingMode.Front
            depthTest = Material.DepthTest.LessEqual
            emissive = Vector4f(0f, 0f, 0f, emissionStrength)
        }

        // Only use time-based elevation when the formal parameter is empty
        // Override by passing LocalDateTime.of($year, $month, $day, $hour, $minute)
        sunDir = initSunDir?.normalize() ?:
            getSunDirFromTime()

        // call a thread to update the sun direction based on local time
        updateSunDir()

    //sunLight.emissionColor = Vector3f(1f, 0.9f, 0.7f)
        //addChild(sunLight)
    }

    /** Turn the current local time into a sun elevation angle, encoded as [Vector3f].
     * @param localTime local time parameter, defaults to [LocalDateTime.now].
     * @param latitude Your latitude in degrees, defaults to 15.0 (central germany).
     */
    private fun getSunDirFromTime(
        localTime: LocalDateTime = LocalDateTime.now()
    ): Vector3f {
        val dayOfYear = localTime.dayOfYear.toDouble()
        val declination = -23.45 * cos(360.0 / 365.0 * (dayOfYear + 10)) // Rough approximation
        val hourAngle = (localTime.hour + localTime.minute / 60.0 - 12) * 15 // Angle from solar noon

        val altitude = asin(
            sin(toRadians(declination))
                * sin(toRadians(latitude))
                + cos(toRadians(declination))
                * cos(toRadians(latitude))
                * cos(toRadians(hourAngle))
        )

        val azimuth = atan2(
            -sin(toRadians(hourAngle)),
            tan(toRadians(declination)) * cos(toRadians(latitude))
                - sin(toRadians(latitude)) * cos(toRadians(hourAngle))
        )

        return Vector3f(
            (sin(azimuth) * cos(altitude)).toFloat(),
            -(cos(azimuth) * cos(altitude)).toFloat(),
            sin(altitude).toFloat()
        ).normalize()
        //TODO fix calculation
    }

    /** Move the shader sun in increments by passing a direction and optionally an increment value.
     * @param arrowKey The direction to be passed as [String].
     * */
    private fun moveSun(arrowKey: String, inc: Float) {
        // Indicate that the user switched to manual sun direction controls
        sunDirEnabledManual = true
        // Define a HashMap to map arrow key dimension strings to rotation angles and axes
        val arrowKeyMappings = HashMap<String, Pair<Float, Vector3f>>()
        logger.info("moving $arrowKey")
        arrowKeyMappings["up"] = Pair(inc, Vector3f(1f, 0f, 0f))
        arrowKeyMappings["down"] = Pair(-inc, Vector3f(1f, 0f, 0f))
        arrowKeyMappings["right"] = Pair(inc, Vector3f(0f, 1f, 0f))
        arrowKeyMappings["left"] = Pair(-inc, Vector3f(0f, 1f, 0f))

        val mapping = arrowKeyMappings[arrowKey]
        if (mapping != null) {
            val (angle, axis) = mapping
            sunDir.rotateAxis(angle, axis.x, axis.y, axis.z)
        }
    }

    /** Attach Up, Down, Left, Right key mappings to the inputhandler to rotate the sun in increments.
     * @param mask Mask key to be used in combination with the arrow keys; defaults to Ctrl.
     * @param inc Increment value for the rotation in degrees, defaults to 10°. */
    fun attachRotateBehaviours(inputHandler: InputHandler, mask: String = "ctrl", inc: Float = 10f) {
        val directions = listOf("up", "down", "left", "right")

        for (direction in directions) {
            val clickBehaviour = ClickBehaviour { _, _ -> moveSun(direction, inc) }
            val bindingName = "move_sun_$direction"
            logger.debug("Attaching behaviour $bindingName to key ${direction.uppercase()}")
            inputHandler.addBehaviour(bindingName, clickBehaviour)
            inputHandler.addKeyBinding(bindingName, "$mask ${direction.uppercase()}")
        }
    }

    /**
     * Spawn a thread
     */
    private fun updateSunDir() {
        thread {
            while (!sunDirEnabledManual) {
                sunDir = getSunDirFromTime(LocalDateTime.of(2023, 9, 15, (random()*12+6).toInt(), 0))
                // Update every 30 seconds
                Thread.sleep(50)
            }
        }
    }
}

//TODO coroutine to update suntimedir
