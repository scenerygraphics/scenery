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

class ParticleGlyphs @JvmOverloads constructor(var positions: List<Vector3f>, var properties: List<Vector2f>, var colors: List<Vector3f>, var forward : Boolean = true) : DefaultNode("ParticleGlyphs"), HasSpatial, HasRenderable,
    HasCustomMaterial<ShaderMaterial>, HasGeometry {

    init {

        addGeometry {
            geometryType = GeometryType.POINTS

            createMaterial()
        }

        geometry{

            vertices = BufferUtils.allocateFloat(positions.size * 3)
            texcoords = BufferUtils.allocateFloat(properties.size * 2)
            normals = BufferUtils.allocateFloat(colors.size * 3)

            val vbuffer = ArrayList<Vector3f>(positions.size * 3)
            val tbuffer = ArrayList<Vector2f>(properties.size * 2)
            val nbuffer = ArrayList<Vector3f>(colors.size * 3)
            var vSize = positions.size
            for (i in 0 until vSize) {
                vbuffer.add(positions[i])
            }
            var tSize = properties.size
            for (i in 0 until tSize) {
                tbuffer.add(properties[i])
            }
            var nSize = colors.size
            for (i in 0 until nSize) {
                nbuffer.add(colors[i])
            }
            vbuffer.forEach { v -> v.get(vertices).position(vertices.position() + 3) }
            tbuffer.forEach { n -> n.get(texcoords).position(texcoords.position() + 2) }
            nbuffer.forEach { n -> n.get(normals).position(normals.position() + 3) }

            vertices.flip()
            texcoords.flip()
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
            override var vertices: FloatBuffer = BufferUtils.allocateFloat(positions.size * 3)
            override var texcoords: FloatBuffer = BufferUtils.allocateFloat(properties.size * 2)
            override var normals: FloatBuffer = BufferUtils.allocateFloat(colors.size * 3)
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
}
