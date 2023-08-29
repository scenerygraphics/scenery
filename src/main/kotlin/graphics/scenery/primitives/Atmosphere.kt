package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.utils.extensions.times
import org.joml.Quaternionf
import org.joml.Vector3f
import java.lang.Math.toRadians
import java.time.LocalDateTime
import kotlin.concurrent.thread
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Implementation of a Nishita sky shader, applied to an [Icosphere] that wraps around the scene.
 * The shader code is ported from Rye Terrells [repository](https://github.com/wwwtyro/glsl-atmosphere).
 * @param initSunPos [Vector3f] of the sun position. Defaults to sun elevation of the current local time.
 * @param radius Radius of the icosphere. Default is `100f`.
 */
open class Atmosphere(initSunPos: Vector3f = Vector3f(0f, 0f, 0f), radius : Float = 100f) :
    Icosphere(radius, 2, insideNormals = true) {

    @ShaderProperty
    var sunPos: Vector3f

    val sunProxy = Icosphere(0.05f * radius, 2)

    //val sunLight = DirectionalLight(sunPos)

    init {
        this.name = "Atmosphere"
        setMaterial(ShaderMaterial.fromClass(this::class.java))
        material {
            cullingMode = Material.CullingMode.Front
        }

        // Only use time-based elevation when the formal parameter is empty
        if (initSunPos.length() == 0f) {
            // Override by passing LocalDateTime.of($year, $month, $day, $hour, $minute)
            sunPos = getSunPosFromTime()
        } else {
            sunPos = initSunPos
        }



        /** Proxy point light to pass the emissive value as @Shaderproperty to the deferred lighting shader. */
        val point = PointLight(1f)
        point.spatial().position = Vector3f()
        point.intensity = 0f
        point.emissive = 1f
        addChild(point)

        sunProxy.name = "sunProxy"
        sunProxy.spatial().position = sunPos.normalize(Vector3f()) * (radius)
        addChild(sunProxy)

        //sunLight.emissionColor = Vector3f(1f, 0.9f, 0.7f)
        //addChild(sunLight)
        sunProxy.postUpdate += {
            sunPos = sunProxy.spatial().position
            //sunLight.spatial().position = sunPos
            //sunLight.spatial().rotation = Quaternionf().set(sunPos.x, sunPos.y, sunPos.z, 0f)
        }


    }

    /** Turn the current local time into a sun elevation angle, encoded as [Vector3f].
     * @param localTime local time parameter, defaults to [LocalDateTime.now].
     * @param latitude Your latitude in degrees, defaults to 15.0 (central germany).
     */
    private fun getSunPosFromTime(
        localTime: LocalDateTime = LocalDateTime.now(),
        latitude: Double = 15.0): Vector3f {
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
}
