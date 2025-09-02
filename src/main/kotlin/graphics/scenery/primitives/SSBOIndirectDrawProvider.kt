package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.buffers.Buffers
import graphics.scenery.attribute.buffers.HasBuffers
import graphics.scenery.attribute.material.Material
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import org.joml.Vector2f
import org.joml.Vector3i
import org.joml.Vector4f
import java.nio.ByteBuffer
import kotlin.jvm.JvmOverloads

/**
 * Constructs a Box [Node] with the dimensions given in [sizes]
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[sizes] The x/y/z sizes of the box
 */
open class SSBOIndirectDrawProvider @JvmOverloads constructor()
    : Mesh("SSBOCainTest"), HasBuffers {

    init {

        material {
            val newMaterial: Material
            newMaterial = ShaderMaterial.fromFiles(
                "SSBOTest.comp"
            )
            metadata["ComputeMetadata"] = ComputeMetadata(
                workSizes = Vector3i(1, 1, 1),
                invocationType = InvocationType.Permanent
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
            addCustom("ssbosInput", Buffers.BufferUsage.Upload, elements = 1, stride = 16) { layout, buffer ->
                // layout points to an UBO object, but should not be named ubo
                layout.add("Color1", { Vector4f(1.0f) })
                buffer as ByteBuffer
                // buffer would return a view of the ByteBuffer used for backing
                buffer.putFloat(0.0f)
                buffer.putFloat(0.1f)
                buffer.putFloat(0.1f)
                buffer.putFloat(1.0f)
                buffer.flip()
            }
            addCustom("ssbosOutput", Buffers.BufferUsage.Upload, elements = 1, stride = 32) { layout, _ ->
                // layout points to an UBO object, but should not be named ubo
                layout.add("Color1", { Vector4f(0.0f) })
                layout.add("Color2", { Vector4f(0.0f) })
            }
            addCustom("ssbosVertices", Buffers.BufferUsage.Upload, elements = 4, stride = 40) { layout, _ ->
                // layout points to an UBO object, but should not be named ubo
                layout.add("Vertex", { Vector4f(0.0f) })
                layout.add("Normal", { Vector4f(0.0f) })
                layout.add("TexCoord", { Vector2f(0.0f) })
            }
            addCustom("ssbosIndices", Buffers.BufferUsage.Upload, elements = 6, stride = 4) { layout, _ ->
                // layout points to an UBO object, but should not be named ubo
                layout.add("Index", { 0 })
            }
            addCustom("ssboDownload", Buffers.BufferUsage.Download, elements = 1, stride = 4) { layout, _ ->
                // layout points to an UBO object, but should not be named ubo
                layout.add("ColorRed", { 0.0f })
            }

        }
    }
}
