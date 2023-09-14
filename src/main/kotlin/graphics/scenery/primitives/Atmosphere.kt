package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.behaviours.MouseDragSphere
import graphics.scenery.controls.behaviours.ToggleCommand
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.scijava.ui.behaviour.Behaviour
import java.lang.Math.toRadians
import java.time.LocalDateTime
import javax.swing.Renderer
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Implementation of a Nishita sky shader, applied to an [Icosphere] that wraps around the scene.
 * The shader code is ported from Rye Terrells [repository](https://github.com/wwwtyro/glsl-atmosphere).
 * @param initSunPos [Vector3f] of the sun position. Defaults to sun elevation of the current local time.
 * @param radius Radius of the icosphere. Default is `100f`.
 */
open class Atmosphere(initSunPos: Vector3f? = null, atmosStrength: Float = 0.3f, latitude: Double = 15.0) :
    Icosphere(10f, 2, insideNormals = true) {
    //TODO update docs
    @ShaderProperty
    var sunDir: Vector3f

    var latitude: Double = latitude
    val sunProxy = Icosphere(1f, 2)

    //val sunLight = DirectionalLight(sunPos)

    init {
        this.name = "Atmosphere"
        setMaterial(ShaderMaterial.fromClass(this::class.java))
        material {
            cullingMode = Material.CullingMode.Front
            depthTest = Material.DepthTest.LessEqual
            emissive = Vector4f(0f, 0f, 0f, atmosStrength)
        }

        // Only use time-based elevation when the formal parameter is empty
        // Override by passing LocalDateTime.of($year, $month, $day, $hour, $minute)
        sunDir = initSunPos?.normalize() ?:
            getSunPosFromTime()

        //sunLight.emissionColor = Vector3f(1f, 0.9f, 0.7f)
        //addChild(sunLight)
    }

    /** Turn the current local time into a sun elevation angle, encoded as [Vector3f].
     * @param localTime local time parameter, defaults to [LocalDateTime.now].
     * @param latitude Your latitude in degrees, defaults to 15.0 (central germany).
     */
    private fun getSunPosFromTime(
        localTime: LocalDateTime = LocalDateTime.now(),
        latitude: Double = this.latitude): Vector3f {
        val dayOfYear = localTime.dayOfYear.toDouble()
        val declination = -23.45 * cos(360.0 / 365.0 * ( dayOfYear + 10 )) // Rough approximation
        val hourAngle = (localTime.hour + localTime.minute / 60.0 - 12) * 15 // Angle from solar noon

        val sunElevation = asin(
            sin(toRadians(declination))
               * sin(toRadians(latitude))
               + cos(toRadians(declination))
               * cos(toRadians(latitude))
               * cos(toRadians(hourAngle))
        )
        logger.info("sun elevation: $sunElevation")
        // Create a vector with the elevation angle as the Y component
        return Vector3f(0.0f, sin(sunElevation).toFloat(), -1.0f).normalize()
    }

    /** Move sun in incremends of 30° by passing the strings "up/down/right/left"*/
    fun moveSun(arrowKey: String) {
        // Define a HashMap to map arrow key dimension strings to rotation angles and axes
        val arrowKeyMappings = HashMap<String, Pair<Float, Vector3f>>()

        // Populate the HashMap with mappings
        arrowKeyMappings["up"] = Pair(30f, Vector3f(1f, 0f, 0f)) // Rotate 30 degrees around X-axis for "up"
        arrowKeyMappings["down"] = Pair(-30f, Vector3f(1f, 0f, 0f)) // Rotate -30 degrees around X-axis for "down"
        arrowKeyMappings["right"] = Pair(30f, Vector3f(0f, 1f, 0f)) // Rotate 30 degrees around Y-axis for "right"
        arrowKeyMappings["left"] = Pair(-30f, Vector3f(0f, 1f, 0f)) // Rotate -30 degrees around Y-axis for "left"

        val mapping = arrowKeyMappings[arrowKey]
        if (mapping != null) {
            val (angle, axis) = mapping
            sunDir.rotateAxis(angle, axis.x, axis.y, axis.z)
        }

    }
}

//TODO coroutine to update suntimepos
