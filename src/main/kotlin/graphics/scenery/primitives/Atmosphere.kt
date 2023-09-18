package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.InputHandler
import kotlinx.coroutines.*
import org.joml.Vector3f
import org.joml.Vector4f
import org.scijava.ui.behaviour.ClickBehaviour
import java.lang.Math.toRadians
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.*

/**
 * Implementation of a Nishita sky shader, applied to an [Icosphere] that wraps around the scene as a skybox.
 * The shader code is ported from Rye Terrells [repository](https://github.com/wwwtyro/glsl-atmosphere).
 * @param initSunDir [Vector3f] of the sun position. Defaults to sun elevation of the current local time.
 * @param emissionStrength Emission strength of the atmosphere shader. Defaults to 0.3f.
 * @param latitude Latitude of the user; needed to calculate the local sun position. Defaults to 50.0, which is central Germany.
 */
open class Atmosphere(initSunDir: Vector3f? = null, emissionStrength: Float = 0.3f, var latitude: Double = 50.0) :
    Icosphere(10f, 2, insideNormals = true) {

    @ShaderProperty
    var sunDir: Vector3f

    //val sunProxy = Icosphere(1f, 2)

    private var sunDirEnabledManual: Boolean = false

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
        if (initSunDir == null) {
            sunDir = getSunDirFromTime()
        }
        else {
            sunDir = initSunDir
            sunDirEnabledManual = true
        }

        // Spawn a coroutine to update the
        val job = CoroutineScope(Dispatchers.Default).launch {
            while (!sunDirEnabledManual) {
                sunDir = getSunDirFromTime()
                // Wait 30 seconds
                delay(30 * 1000)
            }
        }

        //sunLight.emissionColor = Vector3f(1f, 0.9f, 0.7f)
        //addChild(sunLight)
    }

    /** Turn the current local time into a sun elevation angle, encoded as [Vector3f].
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
        if (!sunDirEnabledManual) {
            sunDirEnabledManual = true
            logger.debug("Switched to manual sun direction.")
        }
        // Define a HashMap to map arrow key dimension strings to rotation angles and axes
        val arrowKeyMappings = HashMap<String, Pair<Float, Vector3f>>()
        logger.info("moving $arrowKey")
        arrowKeyMappings["up"] = Pair(increment, Vector3f(1f, 0f, 0f))
        arrowKeyMappings["down"] = Pair(-increment, Vector3f(1f, 0f, 0f))
        arrowKeyMappings["left"] = Pair(increment, Vector3f(0f, 1f, 0f))
        arrowKeyMappings["right"] = Pair(-increment, Vector3f(0f, 1f, 0f))

        val mapping = arrowKeyMappings[arrowKey]
        if (mapping != null) {
            val (angle, axis) = mapping
            sunDir.rotateAxis(toRadians(angle.toDouble()).toFloat(), axis.x, axis.y, axis.z)
        }
    }

    /** Attach Up, Down, Left, Right key mappings to the inputhandler to rotate the sun in increments.
     * @param mask Mask key to be used in combination with the arrow keys; defaults to Ctrl.
     * @param increment Increment value for the rotation in degrees, defaults to 10°. */
    fun attachRotateBehaviours(inputHandler: InputHandler, mask: String = "ctrl", increment: Float = 10f) {
        val directions = listOf("up", "down", "left", "right")
//        val directions = listOf("T", "G", "F", "H")

        for (direction in directions) {
            val clickBehaviour = ClickBehaviour { _, _ -> moveSun(direction, increment) }
            val bindingName = "move_sun_$direction"
            logger.debug("Attaching behaviour $bindingName to key ${direction.uppercase()}")
            inputHandler.addBehaviour(bindingName, clickBehaviour)
            inputHandler.addKeyBinding(bindingName, "$mask ${direction.uppercase()}")
        }
    }
}

