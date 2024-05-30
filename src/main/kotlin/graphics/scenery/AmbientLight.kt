package graphics.scenery

import graphics.scenery.attribute.material.Material
import graphics.scenery.geometry.GeometryType
import org.joml.Vector3f

/**
 * Point light class.
 *
 * Point lights have an extent given as [lightRadius].
 * They also have an optional [Box] to accompany them for easier visualisation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a PointLight with default settings, e.g. white emission color.
 */
class AmbientLight(intensity: Float = 1.0f, emissionColor: Vector3f = Vector3f(1.0f)) : Light("AmbientLight") {
    /** The intensity of the point light. Bound to [0.0, 1.0] if using non-HDR rendering. */
    @ShaderProperty
    override var intensity: Float = intensity

    /** The emission color of the point light. Setting it will also affect the accompanying Box' color. */
    @ShaderProperty
    override var emissionColor: Vector3f = emissionColor

    /** Type of the light, can be DirectionalLight or PointLight */
    @ShaderProperty
    override val lightType: LightType = LightType.AmbientLight

    @ShaderProperty private var lightRadius: Float = 1.0f

    @Suppress("unused") // will be serialised into ShaderProperty buffer
    @ShaderProperty val worldPosition
        get() = Vector3f(0.0f)

    @Suppress("unused") // will be serialised into ShaderProperty buffer
    @ShaderProperty var debugMode = 0

    init {
        // fake geometry
        geometry {
            vertices = BufferUtils.allocateFloatAndPut(
                floatArrayOf(
                    -1.0f, -1.0f, 0.0f,
                    1.0f, -1.0f, 0.0f,
                    1.0f, 1.0f, 0.0f,
                    -1.0f, 1.0f, 0.0f
                )
            )

            normals = BufferUtils.allocateFloatAndPut(
                floatArrayOf(
                    1.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f
                )
            )

            texcoords = BufferUtils.allocateFloatAndPut(
                floatArrayOf(
                    0.0f, 0.0f,
                    1.0f, 0.0f,
                    1.0f, 1.0f,
                    0.0f, 1.0f
                )
            )

            indices = BufferUtils.allocateIntAndPut(
                intArrayOf(0, 1, 2, 0, 2, 3)
            )

            geometryType = GeometryType.TRIANGLES
            vertexSize = 3
            texcoordSize = 2
        }

        material {
            blending.transparent = true
            blending.colorBlending = Blending.BlendOp.add
            blending.sourceColorBlendFactor = Blending.BlendFactor.One
            blending.destinationColorBlendFactor = Blending.BlendFactor.One
            blending.sourceAlphaBlendFactor = Blending.BlendFactor.One
            blending.destinationAlphaBlendFactor = Blending.BlendFactor.One
            blending.alphaBlending = Blending.BlendOp.add
            cullingMode = Material.CullingMode.Front
            depthOp = Material.DepthTest.Greater
        }
    }
}
