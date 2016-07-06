package scenery

import cleargl.GLVector

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Plane(sizes: GLVector) : Mesh(), HasGeometry {
    override var vertices: FloatArray = floatArrayOf()
    override var normals: FloatArray = floatArrayOf()
    override var texcoords: FloatArray = floatArrayOf()
    override var indices: IntArray = intArrayOf()

    override var vertexSize = 3;
    override var texcoordSize = 2;
    override var geometryType = GeometryType.TRIANGLES;

    init {
        this.scale = sizes
        this.name = "plane"
        val side = 2.0f
        val side2 = side / 2.0f

        vertices = floatArrayOf(
                // Front
                -side2, -side2, side2,
                side2, -side2, side2,
                side2,  side2, side2,
                -side2,  side2, side2
        )

        normals = floatArrayOf(
                // Front
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f
        )

        indices = intArrayOf(
                0,1,2,0,2,3
        )

        texcoords = floatArrayOf(
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f
        )
    }
}
