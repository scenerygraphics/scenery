package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.behaviours.WithCameraDelegateBase
import graphics.scenery.utils.extensions.times
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour
import java.time.LocalTime
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.times

/**
 * Implementation of a Nishita sky shader, applied to an [Icosphere] that wraps around the scene.
 * The shader code is ported from Rye Terrells [repository](https://github.com/wwwtyro/glsl-atmosphere).
 * @param name Name of the object. Default is `Atmosphere`.
 * @param sunPos Vector3f of the sun position. Default is `(0f, 0.5f, -1f)`.
 * @param radius Radius of the icosphere. Default is `10f`.
 */
open class Atmosphere(initSunPos: Vector3f = Vector3f(0.0f, 0.5f, -1.0f), radius : Float = 10f) :
    Icosphere(radius, 2, insideNormals = true) {

    @ShaderProperty
    var sunPos = initSunPos

    val sunProxy = Icosphere(0.05f * radius, 2)

    val sunLight = DirectionalLight(sunPos)

    init {
        this.name = "Atmosphere"
        setMaterial(ShaderMaterial.fromClass(this::class.java))
        material {
            cullingMode = Material.CullingMode.Front
        }

        sunPos = getSunPosFromTime(LocalTime.now())

        /** Proxy point light to pass the emissive value as @Shaderproperty to the deferred lighting shader. */
        val point = PointLight(1f)
        point.spatial().position = Vector3f()
        point.intensity = 0f
        point.emissive = 1f
        addChild(point)

        sunProxy.name = "sunProxy"
        sunProxy.spatial().position = sunPos.normalize(Vector3f()) * (radius)
        addChild(sunProxy)

        sunLight.emissionColor = Vector3f(1f, 0.9f, 0.7f)
        //addChild(sunLight)
        sunProxy.postUpdate += {
            sunPos = sunProxy.spatial().position
            //sunLight.spatial().position = sunPos
            //sunLight.spatial().rotation = Quaternionf().set(sunPos.x, sunPos.y, sunPos.z, 0f)
        }


    }

    fun getSunPosFromTime(localTime: LocalTime): Vector3f {
        val localHour = localTime.hour.toFloat() + localTime.minute.toFloat() / 60f
        // Calculate the solar declination (δ) in radians
        val declination = -23.45f * PI.toFloat() / 180f * cos(2f * PI.toFloat() * (localHour + 12f) / 365f)
        // Calculate the hour angle (H) in radians
        val hourAngle = (localHour - 12f) * 15f * PI.toFloat() / 180f

        // Calculate the sun's orientation vector components
        val x = cos(declination) * cos(hourAngle)
        val y = -sin(declination)
        val z = cos(declination) * sin(hourAngle)
        return Vector3f(x, y, z).normalize()
    }

}
