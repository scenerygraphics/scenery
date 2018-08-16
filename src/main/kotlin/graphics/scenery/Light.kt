package graphics.scenery

import cleargl.GLVector

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
}
