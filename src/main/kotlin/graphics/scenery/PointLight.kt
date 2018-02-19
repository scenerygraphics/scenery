package graphics.scenery

import cleargl.GLVector

/**
 * Point light class.
 *
 * Point lights have no extent, but carry a [linear] and [quadratic] falloff.
 * They also have an optional [Box] to accompany them for easier visualisation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a PointLight with default settings, e.g. white emission color.
 */
class PointLight(radius: Float = 0.5f) : Mesh("PointLight") {
    private var proxySphere = Sphere(radius, 21)
    /** The intensity of the point light. Bound to [0.0, 1.0] if using non-HDR rendering. */
    @ShaderProperty var intensity: Float = 0.5f

    /** The emission color of the point light. Setting it will also affect the accompanying Box' color. */
    @ShaderProperty var emissionColor: GLVector = GLVector(1.0f, 1.0f, 1.0f)

    /** Maximum radius in world units */
    @ShaderProperty var lightRadius: Float = radius
        set(value) {
            if(value != lightRadius) {
                logger.info("Resetting light radius")
                field = value
                proxySphere = Sphere(value, 21)
                this.vertices = proxySphere.vertices
                this.normals = proxySphere.normals
                this.texcoords = proxySphere.texcoords

                this.dirty = true
            }
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
        this.vertices = proxySphere.vertices
        this.normals = proxySphere.normals
        this.texcoords = proxySphere.texcoords
        this.geometryType = proxySphere.geometryType
        this.vertexSize = proxySphere.vertexSize
        this.texcoordSize = proxySphere.texcoordSize

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
