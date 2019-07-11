package graphics.scenery

import cleargl.GLVector
import kotlin.math.sqrt
import kotlin.reflect.full.createInstance

/**
 * Abstract class for light [Node]s.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
abstract class Light(name: String = "Light") : Mesh(name) {

    /** Enum class to determine light type. */
    enum class LightType {
        PointLight,
        DirectionalLight
    }

    /** Emission color of the light. */
    @ShaderProperty
    abstract var emissionColor: GLVector

    /** Intensity of the light. */
    @ShaderProperty
    abstract var intensity: Float

    /** The light's type, stored as [LightType]. */
    @ShaderProperty
    abstract val lightType: LightType

    companion object {
        /**
         * Creates a tetrahedron lights of type [T] and returns them as a list.
         * The [center] of the tetrahedron can be specified, as can be the [spread] of it, the light's [intensity], [color], and [radius].
         */
        inline fun <reified T: Light> createLightTetrahedron(center: GLVector = GLVector.getNullVector(3), spread: Float = 1.0f, intensity: Float = 1.0f, color: GLVector = GLVector.getOneVector(3), radius: Float =  5.0f): List<T> {
            val tetrahedron = listOf(
                GLVector(1.0f, 0f, -1.0f/ sqrt(2.0).toFloat()) * spread,
                GLVector(-1.0f,0f,-1.0f/ sqrt(2.0).toFloat()) * spread,
                GLVector(0.0f,1.0f,1.0f/ sqrt(2.0).toFloat()) * spread,
                GLVector(0.0f,-1.0f,1.0f/ sqrt(2.0).toFloat()) * spread)

            return tetrahedron.map { position ->
                val light = T::class.createInstance()
                light.position = position + center
                light.emissionColor = color
                light.intensity = intensity
                if(light is PointLight) {
                    light.lightRadius = radius
                }
                light
            }
        }
    }
}
