package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.InputHandler
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Experimental
import org.joml.Vector3f
import org.joml.Vector4f
import org.scijava.ui.behaviour.ClickBehaviour
import java.lang.Math.toDegrees
import java.lang.Math.toRadians
import java.time.LocalDateTime
import kotlin.math.*
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of a Nishita sky shader, applied to an [Icosphere] that wraps around the scene as a skybox.
 * The shader code is ported from Rye Terrells [repository](https://github.com/wwwtyro/glsl-atmosphere).
 * To move the sun with arrow keys, attach the behaviours using the [attachBehaviors] function.
 * @param initSunDirection [Vector3f] of the sun position. Defaults to sun elevation of the current local time.
 * @param emissionStrength Emission strength of the atmosphere shader. Defaults to 1f.
 * @param latitude Latitude of the user; needed to calculate the local sun position. Defaults to 50.0, which is central Germany.
 */
@Experimental
open class Atmosphere(
    initSunDirection: Vector3f? = null,
    emissionStrength: Float = 1.0f,
    var latitude: Float = 50.0f
) : Icosphere(10f, 2, insideNormals = true) {

    @ShaderProperty
    var sunDirection = Vector3f(1f, 1f, 1f)
        protected set

    /** Is set to true if the user manually moved the sun direction. This disables automatic updating.*/
    var isSunAnimated: Boolean = true
    /** Flag that tracks whether the sun position controls are currently attached to the input handler. */
    var hasControls: Boolean = false
        private set

    var azimuth = 180f
        protected set
    var elevation = 45f
        protected set

    // Coroutine job for updating the sun direction
    private var job = CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.LAZY) {
        logger.debug("Launched sun updating job")
        while (this.coroutineContext.isActive) {
            if (isSunAnimated) {
                setSunPositionFromTime()
            }
            delay(2.seconds)
        }
    }

    // automatically update the material when this property is changed
    var emissionStrength = emissionStrength
        set(value) {
            field = value
            material { emissive = Vector4f(0f, 0f, 0f, emissionStrength * 0.3f) }
        }

    init {
        this.name = "Atmosphere"
        setMaterial(ShaderMaterial.fromClass(this::class.java))
        material {
            cullingMode = Material.CullingMode.Front
            depthTest = true
            depthOp = Material.DepthTest.LessEqual
            emissive = Vector4f(0f, 0f, 0f, emissionStrength * 0.3f)
        }

        // Only animate the sun when no direction is passed to the constructor
        isSunAnimated = if (initSunDirection == null) {
            setSunPositionFromTime()
            true
        } else {
            sunDirection = initSunDirection
            false
        }

        // Spawn a coroutine to update the sun direction
        job.start()
    }

    /** Set the sun direction by the current local time.
     * @param localTime local time parameter, defaults to [LocalDateTime.now].
     */
    fun setSunPositionFromTime(localTime: LocalDateTime = LocalDateTime.now()) {
        val latitudeRad = toRadians(latitude.toDouble())
        val dayOfYear = localTime.dayOfYear.toDouble()
        val declination = toRadians(-23.45 * cos(360.0 / 365.0 * (dayOfYear + 10)))
        val hourAngle = toRadians((localTime.hour + localTime.minute / 60.0 - 12) * 15)

        val elevationRad = asin(
            sin(toRadians(declination))
                * sin(latitudeRad)
                + cos(declination)
                * cos(latitudeRad)
                * cos(hourAngle)
        )

        val azimuthRad = atan2(
            sin(hourAngle),
            cos(hourAngle) * sin(latitudeRad) - tan(declination) * cos(latitudeRad)
        ) - PI / 2

        // update global sun angle properties; these are needed for the sciview inspector fields
        azimuth = toDegrees(azimuthRad).toFloat()
        elevation = toDegrees(elevationRad).toFloat()

        sunDirection = Vector3f(
            cos(azimuthRad).toFloat(),
            sin(elevationRad).toFloat(),
            sin(azimuthRad).toFloat()
        )
        logger.debug("Updated sun direction to {}.", sunDirection)
    }

    /** Set the sun direction by passing a 3D directional vector. */
    fun setSunPosition(direction: Vector3f) {
        isSunAnimated = false
        sunDirection = direction.normalize()
    }

    /** Set the sun direction by passing angles for [elevation] and [azimuth] in degrees. */
    fun setSunPosition(elevation: Float, azimuth: Float) {
        isSunAnimated = false
        this.elevation = elevation
        this.azimuth = azimuth

        sunDirection = Vector3f(
            cos(toRadians(this.azimuth.toDouble())).toFloat(),
            sin(toRadians(this.elevation.toDouble())).toFloat(),
            sin(toRadians(this.azimuth.toDouble())).toFloat()
        )
    }

    /** Move the shader sun in increments by passing a direction and optionally an increment value.
     * @param arrowKey The direction to be passed as [String].
     * */
    private fun moveSun(arrowKey: String, increment: Float) {
        // Indicate that the user switched to manual sun direction controls
        if (isSunAnimated) {
            isSunAnimated = false
            logger.info("Switched to manual sun direction.")
        }

        when (arrowKey) {
            "UP" -> elevation += increment
            "DOWN" -> elevation -= increment
            "LEFT" -> azimuth -= increment
            "RIGHT" -> azimuth += increment
        }
        setSunPosition(elevation, azimuth)
    }

    /** Attach Up, Down, Left, Right key mappings to the inputhandler to rotate the sun in increments.
     * Keybinds are Ctrl + cursor keys for fast movement and Ctrl + Shift + cursor keys for slow movement.
     * Moving the sun will disable the automatic sun animation.
     * @param increment Increment value for the rotation in degrees, defaults to 20°. Slow movement is always 10% of [increment]. */
    fun attachBehaviors(inputHandler: InputHandler, increment: Float = 20f) {
        hasControls = true
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

    /** Detach the key bindings from the input handler.
     *  Per default this also re-enables the sun animation, but it can be turned off with [enableAnimation]. */
    fun detachBehaviors(inputHandler: InputHandler, enableAnimation: Boolean = true) {
        hasControls = false
        if (enableAnimation) {
            isSunAnimated = true
        }
        val behaviors = inputHandler.behaviourMap.keys()
        behaviors.forEach {
            if (it.contains("move_sun")) {
                inputHandler.removeBehaviour(it)
                inputHandler.removeKeyBinding(it)
            }
        }
    }

    /** Attach or detach Up, Down, Left, Right key mappings to the inputhandler to rotate the sun in increments.
     * Keybinds are Ctrl + cursor keys for fast movement and Ctrl + Shift + cursor keys for slow movement.
     * @param increment Increment value for the rotation in degrees, defaults to 20°. Slow movement is always 10% of [increment]. */
    fun toggleBehaviors(inputHandler: InputHandler, increment: Float = 20f) {
        if (hasControls) {
            detachBehaviors(inputHandler)
        } else {
            attachBehaviors(inputHandler, increment)
        }
    }
}

