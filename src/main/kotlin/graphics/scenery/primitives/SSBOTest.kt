package graphics.scenery.primitives

import graphics.scenery.*
import graphics.scenery.attribute.buffers.Buffers
import graphics.scenery.attribute.buffers.HasBuffers
import graphics.scenery.attribute.material.Material
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.geometry.GeometryType
import graphics.scenery.net.Networkable
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector4f
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import kotlin.jvm.JvmOverloads

/**
 * Constructs a Box [Node] with the dimensions given in [sizes]
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[sizes] The x/y/z sizes of the box
 */
open class SSBOTest @JvmOverloads constructor(val sizes: Vector3f = Vector3f(1.0f, 1.0f, 1.0f), val insideNormals: Boolean = false)
    : Mesh("SSBOTest"), HasBuffers {

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
            addCustom("ssbosOutput", Buffers.BufferUsage.Upload, elements = 1, stride = 16, inheritance = true) { layout, _ ->
                // layout points to an UBO object, but should not be named ubo
                layout.add("Color1", { Vector4f(0.0f) })
                layout.add("Color2", { Vector4f(0.0f) })
            }
        }

        val side = 1.0f
        val side2 = side / 2.0f
        geometry {
            geometryType = GeometryType.TRIANGLES

            vertices = BufferUtils.allocateFloatAndPut(floatArrayOf(
                // Front
                -sizes.x() * side2, -side2*sizes.y(), side2*sizes.z(),
                sizes.x() * side2, -side2*sizes.y(), side2*sizes.z(),
                sizes.x() * side2, side2*sizes.y(), side2*sizes.z(),
                -sizes.x() * side2, side2*sizes.y(), side2*sizes.z(),

                // Right
                sizes.x() * side2, -side2*sizes.y(), side2*sizes.z(),
                sizes.x() * side2, -side2*sizes.y(), -side2*sizes.z(),
                sizes.x() * side2, side2*sizes.y(), -side2*sizes.z(),
                sizes.x() * side2, side2*sizes.y(), side2*sizes.z(),

                // Back
                -sizes.x() * side2, -side2*sizes.y(), -side2*sizes.z(),
                -sizes.x() * side2, side2*sizes.y(), -side2*sizes.z(),
                sizes.x() * side2, side2*sizes.y(), -side2*sizes.z(),
                sizes.x() * side2, -side2*sizes.y(), -side2*sizes.z(),

                // Left
                -sizes.x() * side2, -side2*sizes.y(), side2*sizes.z(),
                -sizes.x() * side2, side2*sizes.y(), side2*sizes.z(),
                -sizes.x() * side2, side2*sizes.y(), -side2*sizes.z(),
                -sizes.x() * side2, -side2*sizes.y(), -side2*sizes.z(),

                // Bottom
                -sizes.x() * side2, -side2*sizes.y(), side2*sizes.z(),
                -sizes.x() * side2, -side2*sizes.y(), -side2*sizes.z(),
                sizes.x() * side2, -side2*sizes.y(), -side2*sizes.z(),
                sizes.x() * side2, -side2*sizes.y(), side2*sizes.z(),
                // Top
                -sizes.x() * side2, side2*sizes.y(), side2*sizes.z(),
                sizes.x() * side2, side2*sizes.y(), side2*sizes.z(),
                sizes.x() * side2, side2*sizes.y(), -side2*sizes.z(),
                -sizes.x() * side2, side2*sizes.y(), -side2*sizes.z()
            ))

            val flip: Float = if(insideNormals) { -1.0f } else { 1.0f }
            normals = BufferUtils.allocateFloatAndPut(floatArrayOf(
                // Front
                0.0f, 0.0f, 1.0f*flip,
                0.0f, 0.0f, 1.0f*flip,
                0.0f, 0.0f, 1.0f*flip,
                0.0f, 0.0f, 1.0f*flip,
                // Right
                1.0f*flip, 0.0f, 0.0f,
                1.0f*flip, 0.0f, 0.0f,
                1.0f*flip, 0.0f, 0.0f,
                1.0f*flip, 0.0f, 0.0f,
                // Back
                0.0f, 0.0f, -1.0f*flip,
                0.0f, 0.0f, -1.0f*flip,
                0.0f, 0.0f, -1.0f*flip,
                0.0f, 0.0f, -1.0f*flip,
                // Left
                -1.0f*flip, 0.0f, 0.0f,
                -1.0f*flip, 0.0f, 0.0f,
                -1.0f*flip, 0.0f, 0.0f,
                -1.0f*flip, 0.0f, 0.0f,
                // Bottom
                0.0f, -1.0f*flip, 0.0f,
                0.0f, -1.0f*flip, 0.0f,
                0.0f, -1.0f*flip, 0.0f,
                0.0f, -1.0f*flip, 0.0f,
                // Top
                0.0f, 1.0f*flip, 0.0f,
                0.0f, 1.0f*flip, 0.0f,
                0.0f, 1.0f*flip, 0.0f,
                0.0f, 1.0f*flip, 0.0f
            ))
            indices = BufferUtils.allocateIntAndPut(intArrayOf(
                0, 1, 2, 0, 2, 3,
                4, 5, 6, 4, 6, 7,
                8, 9, 10, 8, 10, 11,
                12, 13, 14, 12, 14, 15,
                16, 17, 18, 16, 18, 19,
                20, 21, 22, 20, 22, 23
            ))
            texcoords = BufferUtils.allocateFloatAndPut(floatArrayOf(
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f
            ))
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
        return SSBOTest(sizes,insideNormals)
    }
}
