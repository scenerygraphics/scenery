package scenery

import BufferUtils
import cleargl.GLVector
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Constructs a plane with the dimensions given in [sizes].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @param[sizes] The dimensions of the plane.
 */
open class Plane(sizes: GLVector) : Mesh(), HasGeometry {
    override var vertices: FloatBuffer = FloatBuffer.allocate(0)
    override var normals: FloatBuffer = FloatBuffer.allocate(0)
    override var texcoords: FloatBuffer = FloatBuffer.allocate(0)
    override var indices: IntBuffer = IntBuffer.allocate(0)

    override var vertexSize = 3;
    override var texcoordSize = 2;
    override var geometryType = GeometryType.TRIANGLES;

    init {
        this.scale = sizes
        this.name = "plane"
        val side = 2.0f
        val side2 = side / 2.0f

        vertices = BufferUtils.allocateFloatAndPut(floatArrayOf(
                // Front
                -side2, -side2, side2,
                side2, -side2, side2,
                side2,  side2, side2,
                -side2,  side2, side2
        ))

        normals = BufferUtils.allocateFloatAndPut(floatArrayOf(
                // Front
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f
        ))

        indices = BufferUtils.allocateIntAndPut(intArrayOf(
                0,1,2,0,2,3
        ))

        texcoords = BufferUtils.allocateFloatAndPut(floatArrayOf(
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f
        ))
    }
}
