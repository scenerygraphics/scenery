package graphics.scenery

import cleargl.GLVector

/**
 * Point light class.
 *
 * Point lights have an extent given as [lightRadius].
 * They also have an optional [Box] to accompany them for easier visualisation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a PointLight with default settings, e.g. white emission color.
 */
class PointLight(radius: Float = 5.0f) : Mesh("PointLight") {
    private var proxySphere = Sphere(radius * 1.1f, 10)
    /** The intensity of the point light. Bound to [0.0, 1.0] if using non-HDR rendering. */
    @ShaderProperty var intensity: Float = 100.0f

    /** The emission color of the point light. Setting it will also affect the accompanying Box' color. */
    @ShaderProperty var emissionColor: GLVector = GLVector(1.0f, 1.0f, 1.0f)

    /** Maximum radius in world units */
    @Suppress("unused") // will be serialised into ShaderProperty buffer
    @ShaderProperty var lightRadius: Float = radius
        set(value) {
            if(value != lightRadius) {
                logger.info("Resetting light radius")
                field = value
                proxySphere = Sphere(value * 1.1f, 10)
                this.vertices = proxySphere.vertices
                this.normals = proxySphere.normals
                this.texcoords = proxySphere.texcoords

                this.dirty = true
            }
        }

    /** Node name of the Point Light */
    override var name = "PointLight"

    @Suppress("unused") // will be serialised into ShaderProperty buffer
    @ShaderProperty val worldPosition
        get(): GLVector =
            if(this.parent != null && this.parent !is Scene) {
                this.world.mult(GLVector(position.x(), position.y(), position.z(), 1.0f))
            } else {
                GLVector(position.x(), position.y(), position.z(), 1.0f)
            }

    @Suppress("unused") // will be serialised into ShaderProperty buffer
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
