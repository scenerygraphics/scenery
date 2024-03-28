package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.InputHandler
import kotlinx.coroutines.*
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.scijava.ui.behaviour.ClickBehaviour
import java.lang.Math.toRadians
import java.time.LocalDateTime
import kotlin.collections.HashMap
import kotlin.math.*

/**
 * Implementation of a Nishita sky shader, applied to an [Icosphere] that wraps around the scene as a skybox.
 * The shader code is ported from Rye Terrells [repository](https://github.com/wwwtyro/glsl-atmosphere).
 * To move the sun with arrow keybinds, attach the behaviours using the [attachRotateBehaviours] function.
 * @param initSunDir [Vector3f] of the sun position. Defaults to sun elevation of the current local time.
 * @param emissionStrength Emission strength of the atmosphere shader. Defaults to 0.3f.
 * @param latitude Latitude of the user; needed to calculate the local sun position. Defaults to 50.0, which is central Germany.
 */
open class Atmosphere(initSunDir: Vector3f? = null, emissionStrength: Float = 0.3f, var latitude: Double = 50.0) :
    Icosphere(10f, 2, insideNormals = true) {

    @ShaderProperty
    var sunDir: Vector3f

    private var sunDirectionManual: Boolean = false

    init {
        this.name = "Atmosphere"
        setMaterial(ShaderMaterial.fromClass(this::class.java))
        material {
            cullingMode = Material.CullingMode.Front
            depthOp = Material.DepthTest.LessEqual
            emissive = Vector4f(0f, 0f, 0f, emissionStrength)
        }

        // Only use time-based elevation when the formal parameter is empty
        if (initSunDir == null) {
            sunDir = getSunDirFromTime()
        }
        else {
            sunDir = initSunDir
            sunDirectionManual = true
        }

        // Spawn a coroutine to update the sun direction
        val job = CoroutineScope(Dispatchers.Default).launch {
            while (!sunDirectionManual) {
                sunDir = getSunDirFromTime()
                // Wait 30 seconds
                delay(30 * 1000)
            }
        }
    }

    /** Turn the current local time into a sun elevation angle, encoded as cartesian  [Vector3f].
     * @param localTime local time parameter, defaults to [LocalDateTime.now].
     */
    private fun getSunDirFromTime(localTime: LocalDateTime = LocalDateTime.now()): Vector3f {
        val latitudeRad = toRadians(latitude)
        val dayOfYear = localTime.dayOfYear.toDouble()
        val declination = toRadians(-23.45 * cos(360.0 / 365.0 * (dayOfYear + 10)))
        val hourAngle = toRadians((localTime.hour + localTime.minute / 60.0 - 12) * 15)

        val elevation = asin(
            sin(toRadians(declination))
                * sin(latitudeRad)
                + cos(declination)
                * cos(latitudeRad)
                * cos(hourAngle)
        )

        val azimuth = atan2(
            sin(hourAngle),
            cos(hourAngle) * sin(latitudeRad) - tan(declination) * cos(latitudeRad)
        ) - PI / 2

        val result = Vector3f(
            cos(azimuth).toFloat(),
            sin(elevation).toFloat(),
            sin(azimuth).toFloat()
        )
        logger.debug("Updated sun direction to {}.", result)
        return result
    }

    /** Move the shader sun in increments by passing a direction and optionally an increment value.
     * @param arrowKey The direction to be passed as [String].
     * */
    private fun moveSun(arrowKey: String, increment: Float) {
        // Indicate that the user switched to manual sun direction controls
        if (!sunDirectionManual) {
            sunDirectionManual = true
            logger.info("Switched to manual sun direction.")
        }
        // Define a HashMap to map arrow key dimension strings to rotation angles and axes
        val arrowKeyMappings = HashMap<String, Pair<Float, Vector3f>>()
        arrowKeyMappings["UP"] = Pair(increment, Vector3f(1f, 0f, 0f))
        arrowKeyMappings["DOWN"] = Pair(-increment, Vector3f(1f, 0f, 0f))
        arrowKeyMappings["LEFT"] = Pair(increment, Vector3f(0f, 1f, 0f))
        arrowKeyMappings["RIGHT"] = Pair(-increment, Vector3f(0f, 1f, 0f))

        val mapping = arrowKeyMappings[arrowKey]
        if (mapping != null) {
            val (angle, axis) = mapping
            val rotation = Quaternionf().rotationAxis(toRadians(angle.toDouble()).toFloat(), axis.x, axis.y, axis.z)
            sunDir.rotate(rotation)
        }
    }

    /** Attach Up, Down, Left, Right key mappings to the inputhandler to rotate the sun in increments.
     * Keybinds are Ctrl + cursor keys for fast movement and Ctrl + Shift + cursor keys for slow movement.
     * @param increment Increment value for the rotation in degrees, defaults to 20°. Slow movement is always 10% of [increment]. */
    fun attachRotateBehaviours(inputHandler: InputHandler, increment: Float = 20f) {
        val incMap = mapOf(
            "fast" to increment,
            "slow" to increment / 10
        )
        for (speed in listOf("fast", "slow")) {
            for (direction in listOf("UP", "DOWN", "LEFT", "RIGHT")) {
                val clickBehaviour = ClickBehaviour { _, _ -> incMap[speed]?.let { moveSun(direction, it) } }
                val bindingName = "move_sun_${direction}_$speed"
                val bindingKey = if (speed == "slow") "ctrl shift $direction" else "ctrl $direction"
                logger.debug("Attaching behaviour $bindingName to key $direction")
                inputHandler.addBehaviour(bindingName, clickBehaviour)
                inputHandler.addKeyBinding(bindingName, bindingKey)
            }
        }
    }
}

