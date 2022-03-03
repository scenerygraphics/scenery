package graphics.scenery

import graphics.scenery.geometry.GeometryType
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Shaders
import org.joml.Vector2f

/**
 *
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Billboard(sizes: Vector2f) : Mesh("Billboard") {
    init {
        geometry {
            this.vertices = BufferUtils.allocateFloatAndPut(
                floatArrayOf(
                    -0.5f*sizes.x, -0.5f*sizes.y, 0.0f,
                    0.5f*sizes.x, -0.5f*sizes.y, 0.0f,
                    0.5f*sizes.x, 0.5f*sizes.y, 0.0f,
                    -0.5f*sizes.x, 0.5f*sizes.y, 0.0f))

            this.normals = BufferUtils.allocateFloatAndPut(
                floatArrayOf(
                    1.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f))

            this.texcoords = BufferUtils.allocateFloatAndPut(
                floatArrayOf(
                    0.0f, 0.0f,
                    1.0f, 0.0f,
                    1.0f, 1.0f,
                    0.0f, 1.0f))

            this.indices = BufferUtils.allocateIntAndPut(
                intArrayOf(0, 1, 2, 0, 2, 3))

            this.geometryType = GeometryType.TRIANGLES
            this.vertexSize = 3
            this.texcoordSize = 2
        }

        renderable {
            isBillboard = true
        }

        setMaterial(ShaderMaterial.fromFiles("Billboard.vert", "Billboard.frag")) {
            cullingMode = Material.CullingMode.None
            blending.transparent = true
            blending.sourceColorBlendFactor = Blending.BlendFactor.One
            blending.destinationColorBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
            blending.sourceAlphaBlendFactor = Blending.BlendFactor.One
            blending.destinationAlphaBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
            blending.colorBlending = Blending.BlendOp.add
            blending.alphaBlending = Blending.BlendOp.add
        }
    }
}
