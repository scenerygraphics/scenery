package scenery

import cleargl.GLVector

/**
 * Constructs a Box [Node] with the dimensions given in [sizes]
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[sizes] The x/y/z sizes of the box
 */
open class Box(sizes: GLVector) : Mesh("box"), HasGeometry {
    override var vertices: FloatArray = floatArrayOf()
    override var normals: FloatArray = floatArrayOf()
    override var texcoords: FloatArray = floatArrayOf()
    override var indices: IntArray = intArrayOf()

    override var vertexSize = 3;
    override var texcoordSize = 2;
    override var geometryType = GeometryType.TRIANGLES;

    init {
        val side = 1.0f
        val side2 = side / 2.0f

        vertices = floatArrayOf(
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
        )

        normals = floatArrayOf(
            // Front
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            // Right
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            // Back
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            // Left
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            // Bottom
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            // Top
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f
        )

        indices = intArrayOf(
            0, 1, 2, 0, 2, 3,
            4, 5, 6, 4, 6, 7,
            8, 9, 10, 8, 10, 11,
            12, 13, 14, 12, 14, 15,
            16, 17, 18, 16, 18, 19,
            20, 21, 22, 20, 22, 23
        )

        texcoords = floatArrayOf(
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
        )
    }
}
