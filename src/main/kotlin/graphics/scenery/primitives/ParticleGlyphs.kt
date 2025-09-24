package graphics.scenery.primitives

import graphics.scenery.BufferUtils
import graphics.scenery.DefaultNode
import graphics.scenery.ShaderMaterial
import graphics.scenery.attribute.geometry.DefaultGeometry
import graphics.scenery.attribute.geometry.Geometry
import graphics.scenery.backends.ShaderType
import graphics.scenery.geometry.GeometryType
import graphics.scenery.attribute.geometry.HasGeometry
import graphics.scenery.attribute.material.HasCustomMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.attribute.renderable.HasRenderable
import graphics.scenery.attribute.spatial.HasSpatial
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Vector2f

import org.joml.Vector3f
import java.nio.FloatBuffer
import java.util.ArrayList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ParticleGlyphs @JvmOverloads constructor(var positions: FloatBuffer, var properties: FloatBuffer, var colors: FloatBuffer, var forward : Boolean = true) : DefaultNode("ParticleGlyphs"), HasSpatial, HasRenderable,
    HasCustomMaterial<ShaderMaterial>, HasGeometry {

    init {

        addGeometry {
            geometryType = GeometryType.POINTS

            createMaterial()
        }

        geometry{

            vertices = BufferUtils.allocateFloat(positions.limit())
            vertices.put(positions)
            vertices.flip()

            texcoords = BufferUtils.allocateFloat(properties.limit())
            texcoords.put(properties)
            texcoords.flip()

            normals = BufferUtils.allocateFloat(colors.limit())
            normals.put(colors)
            normals.flip()
        }

        addMaterial {
            cullingMode = Material.CullingMode.None
        }

        addRenderable()

        addSpatial()
    }

    override fun createGeometry(): Geometry {
        return object: DefaultGeometry(this) {
            override var vertices: FloatBuffer = BufferUtils.allocateFloat(positions.limit())
            override var texcoords: FloatBuffer = BufferUtils.allocateFloat(properties.limit())
            override var normals: FloatBuffer = BufferUtils.allocateFloat(colors.limit())
        }
    }

    override fun createMaterial(): ShaderMaterial {
        val newMaterial: ShaderMaterial

        if(forward) {
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
