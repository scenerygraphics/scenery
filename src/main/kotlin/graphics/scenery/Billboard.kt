package graphics.scenery

import graphics.scenery.geometry.GeometryType
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Shaders
import org.joml.Vector2f
import org.joml.Vector3f

/**
 *
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Billboard(sizes: Vector2f, position: Vector3f = Vector3f(0.0f, 0.0f, 0.0f), useGeometryShader: Boolean = false) : Mesh("Billboard") {
    /** The [ShaderProperty] storing whether the biilboard should use vertex shader based billboarding (not working) or geometry shader billboarding. */
    @ShaderProperty
    var UseGeometryBillboarding: Int = 1
    init {
        if(useGeometryShader) {
            UseGeometryBillboarding = 1;
            addGeometry {
                geometryType = GeometryType.POINTS

                createMaterial()
            }
            geometry {
                vertices = BufferUtils.allocateFloatAndPut(
                    floatArrayOf(
                        position.x, position.y, position.z
                    )
                )
                normals = BufferUtils.allocateFloatAndPut(
                    floatArrayOf(
                        sizes.x, sizes.y, 0.0f
                    )
                )
                addRenderable()
                addSpatial()
            }
        }
        else {
            UseGeometryBillboarding = 0;
            geometry {
                vertices = BufferUtils.allocateFloatAndPut(
                    floatArrayOf(
                        position.x + -0.5f * sizes.x, position.y + -0.5f * sizes.y, position.z + 0.0f,
                        position.x + 0.5f * sizes.x, position.y + -0.5f * sizes.y, position.z + 0.0f,
                        position.x + 0.5f * sizes.x, position.y + 0.5f * sizes.y, position.z + 0.0f,
                        position.x + -0.5f * sizes.x, position.y + 0.5f * sizes.y, position.z + 0.0f
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

                this.geometryType = GeometryType.TRIANGLES
                this.vertexSize = 3
                this.texcoordSize = 2
            }

            setMaterial(ShaderMaterial.fromFiles("${this::class.java.simpleName}.vert", "${this::class.java.simpleName}.frag")) {
                cullingMode = Material.CullingMode.None
                blending.transparent = true
            }
        }
    }

    override fun createMaterial(): ShaderMaterial {
        val newMaterial: ShaderMaterial
        newMaterial = ShaderMaterial.fromFiles(
            "${this::class.java.simpleName}.vert",
            "${this::class.java.simpleName}.geom",
            "${this::class.java.simpleName}.frag"
        )

        setMaterial(newMaterial) {
            newMaterial.diffuse = diffuse
            newMaterial.specular = specular
            newMaterial.ambient = ambient
            newMaterial.metallic = metallic
            newMaterial.roughness = roughness
            newMaterial.blending.transparent = true
            cullingMode = Material.CullingMode.Back
        }

        return newMaterial
    }
}
