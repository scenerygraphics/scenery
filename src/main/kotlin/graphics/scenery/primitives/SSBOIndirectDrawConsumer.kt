package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.buffers.Buffers
import graphics.scenery.attribute.buffers.HasBuffers
import graphics.scenery.attribute.material.Material
import graphics.scenery.geometry.GeometryType
import graphics.scenery.net.Networkable
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Constructs a Box [Node] with the dimensions given in [sizes]
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[sizes] The x/y/z sizes of the box
 */
open class SSBOIndirectDrawConsumer @JvmOverloads constructor(val sizes: Vector3f = Vector3f(1.0f, 1.0f, 1.0f), val insideNormals: Boolean = false)
    : Mesh("IndirectDraw"), HasBuffers {

    init {

        material {
            val newMaterial: Material
            newMaterial = ShaderMaterial.fromFiles(
                "DefaultDeferred.vert",
                "SSBOTest.frag"
            )
            setMaterial(newMaterial) {
                newMaterial.diffuse = diffuse
                newMaterial.specular = specular
                newMaterial.ambient = ambient
                newMaterial.metallic = metallic
                newMaterial.roughness = roughness

                blending.opacity = 1.0f
                blending.transparent = false
                blending.setOverlayBlending()

                cullingMode = Material.CullingMode.None
            }
        }
        addBuffers()
        buffers {
            // size is by default determined by the UBO layout, but can be given as optional parameter.
            // elements is mandatory
            addCustom("ssbosOutput", Buffers.BufferUsage.Upload, elements = 1, stride = 32, inheritance = true) { layout, _ ->
                // layout points to an UBO object, but should not be named ubo
                layout.add("Color1", { Vector4f(0.0f) })
                layout.add("Color2", { Vector4f(0.0f) })
            }
            addCustom("ssbosVertices", Buffers.BufferUsage.Upload, elements = 4, stride = 40, inheritance = true) { layout, _ ->
                // layout points to an UBO object, but should not be named ubo
                layout.add("Vertex", { Vector4f(0.0f) })
                layout.add("Normal", { Vector4f(0.0f) })
                layout.add("TexCoord", { Vector2f(0.0f) })
            }
            addCustom("ssbosIndices", Buffers.BufferUsage.Upload, elements = 6, stride = 4, inheritance = true) { layout, _ ->
                // layout points to an UBO object, but should not be named ubo
                layout.add("Index", { 0 })
            }
        }

        val side = 1.0f
        val side2 = side / 2.0f
        geometry {
            shaderSourced = true
            geometryType = GeometryType.TRIANGLES

            addCustomVertexLayout { layout ->
                layout.add("Position",  { Vector3f(0.0f)}, 0)
                layout.add("Normal",  { Vector3f(0.0f)}, 16)
                layout.add("UV",  { Vector2f(0.0f)},  32)
            }
        }

        boundingBox = OrientedBoundingBox(this,
            -side2 * sizes.x(),
            -side2 * sizes.y(),
            -side2 * sizes.z(),
            side2 * sizes.x(),
            side2 * sizes.y(),
            side2 * sizes.z())
        boundingBox = generateBoundingBox()
    }

    override fun getConstructorParameters(): Any? {
        return sizes to insideNormals
    }

    override fun constructWithParameters(parameters: Any, hub: Hub): Networkable {
        val pair = parameters as Pair<*,*>
        val sizes = pair.first as? Vector3f
        val insideNormals = pair.second as? Boolean
        if (sizes == null || insideNormals == null){
            throw IllegalArgumentException()
        }
        return SSBOIndirectDrawConsumer(sizes, insideNormals)
    }
}
