package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.utils.extensions.times
import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour

/**
 * Implementation of a Nishita sky shader, applied to an [Icosphere] that wraps around the scene.
 * The shader code is ported from Rye Terrells [repository](https://github.com/wwwtyro/glsl-atmosphere).
 * @param name Name of the object. Default is `Atmosphere`.
 * @param sunPos Vector3f of the sun position. Default is `(0f, 0.5f, -1f)`.
 * @param radius Radius of the icosphere. Default is `10f`.
 */
open class Atmosphere(name: String = "Atmosphere", sunPos: Vector3f = Vector3f(0f, 0.5f, -1f), radius : Float = 10f) :
    Icosphere(radius, 2, insideNormals = true) {

    @ShaderProperty
    var sunPos = sunPos



    init {
        setMaterial(ShaderMaterial.fromClass(this::class.java))
        material{
            cullingMode = Material.CullingMode.Front
        }

        /** Proxy point light to pass the emissive value as @Shaderproperty to the deferred lighting shader. */
        val point = PointLight(1f)
        point.spatial().position = Vector3f()
        point.intensity = 0f
        point.emissive = 1f
        logger.info(point.emissive.toString())
        addChild(point)


        val sunProxy = Box(Vector3f(0.5f))
        sunProxy.spatial().position = sunPos.normalize() * (radius)
        addChild(sunProxy)
    }
}
