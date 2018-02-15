package graphics.scenery

import cleargl.GLVector
import kotlin.reflect.KProperty

/**
 * Point light class.
 *
 * Point lights have no extent, but carry a [linear] and [quadratic] falloff.
 * They also have an optional [Box] to accompany them for easier visualisation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a PointLight with default settings, e.g. white emission color.
 */
class PointLight : Sphere(1.0f, 21) {
    /** The intensity of the point light. Bound to [0.0, 1.0] if using non-HDR rendering. */
    @ShaderProperty var intensity: Float = 0.5f

    /** The emission color of the point light. Setting it will also affect the accompanying Box' color. */
    @ShaderProperty var emissionColor: GLVector = GLVector(1.0f, 1.0f, 1.0f)

    /** Maximum radius in world units */
    @ShaderProperty var lightRadius: Float = 0.5f
        set(value) {
            field = value
            scale = GLVector(2.0f*value, 2.0f*value, 2.0f*value)
        }

    /** Node name of the Point Light */
    override var name = "PointLight"

    /** Linear falloff of the light. */
    @ShaderProperty var linear: Float = 10.5f

    /** Quadratic falloff of the light. */
    @ShaderProperty var quadratic: Float = 2.7f

    @ShaderProperty val worldPosition
        get() = this.world.mult(GLVector(position.x(), position.y(), position.z(), 1.0f))

    @ShaderProperty var debugMode = 0

    init {
        material.blending.transparent = true
        material.blending.colorBlending = Blending.BlendOp.add
        material.blending.sourceColorBlendFactor = Blending.BlendFactor.One
        material.blending.destinationColorBlendFactor = Blending.BlendFactor.One
        material.blending.sourceAlphaBlendFactor = Blending.BlendFactor.One
        material.blending.destinationAlphaBlendFactor = Blending.BlendFactor.One
        material.blending.alphaBlending = Blending.BlendOp.add
        material.cullingMode = Material.CullingMode.Front
        material.depthTest = Material.DepthTest.Greater
    }
}
