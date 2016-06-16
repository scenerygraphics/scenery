package scenery

import cleargl.GLVector

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Plane(sizes: GLVector) : Node("plane"), HasGeometry {
    override var vertices: FloatArray = floatArrayOf()
    override var normals: FloatArray = floatArrayOf()
    override var texcoords: FloatArray = floatArrayOf()
    override var indices: IntArray = intArrayOf()

    override val vertexSize = 3;
    override val texcoordSize = 2;
    override val geometryType = GeometryType.TRIANGLES;

    init {
        this.scale = sizes
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
                0.0f, 1.0f,
                0.0f, 0.0f
        )
    }
}
