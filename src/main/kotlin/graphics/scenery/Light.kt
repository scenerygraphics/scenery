package graphics.scenery

import cleargl.GLVector

abstract class Light(name: String = "Light") : Mesh(name) {
    enum class LightType {
        PointLight,
        DirectionalLight
    }

    @ShaderProperty
    abstract var emissionColor: GLVector

    @ShaderProperty
    abstract var intensity: Float

    @ShaderProperty
    abstract val lightType: LightType
}
