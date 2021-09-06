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


import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

class ParticleGlyphs @JvmOverloads constructor(var positions: List<Vector3f>, var properties: List<Vector3f>) : DefaultNode("ParticleGlyphs"), HasSpatial, HasRenderable,
    HasCustomMaterial<ShaderMaterial>, HasGeometry {

    init {

        addGeometry {
            geometryType = GeometryType.POINTS

            createMaterial()
        }

        geometry{
            vertices.position(0)
            vertices.limit(positions.size * 3)
            positions.forEach { v ->
                v.get(vertices)
                vertices.position(vertices.position() + 3)
            }
            vertices.flip()

            normals.position(0)
            normals.limit(properties.size * 3)
            properties.forEach { v ->
                v.get(normals)
                normals.position(normals.position() + 3)
            }
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
            override var normals: FloatBuffer = BufferUtils.allocateFloat(properties.size * 3)
        }
    }

    override fun createMaterial(): ShaderMaterial {
        val newMaterial: ShaderMaterial


        newMaterial = ShaderMaterial.fromClass(
            this::class.java,
            listOf(ShaderType.VertexShader, ShaderType.GeometryShader, ShaderType.FragmentShader)
        )

        setMaterial(newMaterial) {
            newMaterial.diffuse = diffuse
            newMaterial.specular = specular
            newMaterial.ambient = ambient
            newMaterial.metallic = metallic
            newMaterial.roughness = roughness

            newMaterial.blending.transparent = true
            cullingMode = Material.CullingMode.None
        }

        return newMaterial
    }
}
