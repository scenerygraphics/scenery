package graphics.scenery.primitives

import graphics.scenery.BufferUtils
import graphics.scenery.DefaultNode
import graphics.scenery.ShaderMaterial
import graphics.scenery.attribute.buffers.Buffers
import graphics.scenery.attribute.buffers.HasBuffers
import graphics.scenery.attribute.geometry.DefaultGeometry
import graphics.scenery.attribute.geometry.Geometry
import graphics.scenery.backends.ShaderType
import graphics.scenery.geometry.GeometryType
import graphics.scenery.attribute.geometry.HasGeometry
import graphics.scenery.attribute.material.HasCustomMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.attribute.renderable.HasRenderable
import graphics.scenery.attribute.spatial.HasSpatial
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer

import java.nio.FloatBuffer

class ParticleGlyph @JvmOverloads constructor(var positions: FloatBuffer, var radii: FloatBuffer, var colors: FloatBuffer, var forward : Boolean = true) : DefaultNode("ParticleGlyphs"), HasSpatial, HasRenderable,
    HasCustomMaterial<ShaderMaterial>, HasGeometry, HasBuffers {

    init {

        addBuffers()
        buffers {
            // size is by default determined by the UBO layout, but can be given as optional parameter.
            // elements is mandatory
            addCustom("ssboParticleData", Buffers.BufferUsage.Upload, elements = positions.limit(), stride = 4*4 + 3*4) { layout, buffer ->
                // layout points to an UBO object, but should not be named ubo
                layout.add("PositionRadius", { Vector4f(1.0f) })
                layout.add("PositionColor", { Vector3f(1.0f) })
                buffer as ByteBuffer
                // buffer would return a view of the ByteBuffer used for backing
                for(i in positions.array().indices step 3) {
                    buffer.putFloat(positions[i])
                    buffer.putFloat(positions[i+1])
                    buffer.putFloat(positions[i+2])

                    buffer.putFloat(radii[i/3])

                    buffer.putFloat(colors[i])
                    buffer.putFloat(colors[i+1])
                    buffer.putFloat(colors[i+2])
                }
                buffer.flip()
            }
            addCustom("ssboParticleSilhouettes", Buffers.BufferUsage.UploadAndDownload, elements = positions.limit(), stride = 3*4 + 2*4 + 4*4 + 3*4) { layout, buffer ->
                layout.add("Position", { Vector3f(1.0f) })
                layout.add("TextureCoordinate", { Vector2f(1.0f) })
                layout.add("CenterRadius", { Vector4f(1.0f) })
                layout.add("Color", { Vector3f(1.0f) })
            }
        }

        /*addGeometry {
            geometryType = GeometryType.POINTS

            createMaterial()
        }

        geometry{

            vertices = BufferUtils.allocateFloat(positions.limit())
            vertices.put(positions)
            vertices.flip()


            texcoords = BufferUtils.allocateFloat(radii.limit())
            texcoords.put(radii)
            texcoords.flip()

            normals = BufferUtils.allocateFloat(colors.limit())
            normals.put(colors)
            normals.flip()
        }*/

        addMaterial {
            cullingMode = Material.CullingMode.None
        }

        addRenderable()

        addSpatial()
    }

    override fun createGeometry(): Geometry {
        return object: DefaultGeometry(this) {
            /*override var vertices: FloatBuffer = BufferUtils.allocateFloat(positions.limit())
            override var texcoords: FloatBuffer = BufferUtils.allocateFloat(radii.limit())
            override var normals: FloatBuffer = BufferUtils.allocateFloat(colors.limit())*/
        }
    }

    override fun createMaterial(): ShaderMaterial {
        val newMaterial: ShaderMaterial

        /*if(forward) {
            newMaterial = ShaderMaterial.fromFiles(
                "${this::class.java.simpleName}.vert",
                "${this::class.java.simpleName}.geom",
                "${this::class.java.simpleName}Forward.frag"
            )
        }
        else
        {
            newMaterial = ShaderMaterial.fromClass(
                this::class.java,
                listOf(ShaderType.VertexShader, ShaderType.GeometryShader, ShaderType.FragmentShader)
            )
        }*/
        if(forward) {
            newMaterial = ShaderMaterial.fromFiles(
                "${this::class.java.simpleName}.comp",
                "${this::class.java.simpleName}.vert",
                "${this::class.java.simpleName}Forward.frag"
            )
        }
        else
        {
            newMaterial = ShaderMaterial.fromClass(
                this::class.java,
                listOf(ShaderType.ComputeShader, ShaderType.VertexShader, ShaderType.FragmentShader)
            )
        }

        setMaterial(newMaterial) {
            newMaterial.diffuse = diffuse
            newMaterial.specular = specular
            newMaterial.ambient = ambient
            newMaterial.metallic = metallic
            newMaterial.roughness = roughness
            newMaterial.blending.transparent = forward

            cullingMode = Material.CullingMode.None
        }

        return newMaterial
    }

    fun updatePositions(positions: FloatBuffer)
    {
        geometry{
            vertices = positions
            dirty = true
        }
    }

    fun updateProperties(propertiesRadius: FloatBuffer, propertiesColor: FloatBuffer)
    {
        geometry{
            normals = propertiesColor
            texcoords = propertiesRadius
            dirty = true
        }
    }
}
