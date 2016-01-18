package scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.GL
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Created by ulrik on 14/12/15.
 */
class Box(sizes: GLVector) : GeometricalObject(3, GL.GL_TRIANGLES) {
    var vertices: FloatArray? = null
    var normals: FloatArray? = null
    var texcoords: FloatArray? = null
    var indices: IntArray? = null
    var sizes: GLVector

    init {
         this.sizes = sizes
         val side = 2.0f
         val side2 = side / 2.0f

         vertices = floatArrayOf(
            // Front
            -side2, -side2, side2,
            side2, -side2, side2,
            side2,  side2, side2,
            -side2,  side2, side2,
            // Right
            side2, -side2, side2,
            side2, -side2, -side2,
            side2,  side2, -side2,
            side2,  side2, side2,
            // Back
            -side2, -side2, -side2,
            -side2,  side2, -side2,
            side2,  side2, -side2,
            side2, -side2, -side2,
            // Left
            -side2, -side2, side2,
            -side2,  side2, side2,
            -side2,  side2, -side2,
            -side2, -side2, -side2,
            // Bottom
            -side2, -side2, side2,
            -side2, -side2, -side2,
            side2, -side2, -side2,
            side2, -side2, side2,
            // Top
            -side2,  side2, side2,
            side2,  side2, side2,
            side2,  side2, -side2,
            -side2,  side2, -side2
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
            0,1,2,0,2,3,
            4,5,6,4,6,7,
            8,9,10,8,10,11,
            12,13,14,12,14,15,
            16,17,18,16,18,19,
            20,21,22,20,22,23
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

    override fun init(): Boolean {
        // null GLSL program, aka use the default shaders
        val program = null

        super.init()

        setVerticesAndCreateBuffer(FloatBuffer.wrap(vertices))
        setNormalsAndCreateBuffer(FloatBuffer.wrap(normals))
        setTextureCoordsAndCreateBuffer(FloatBuffer.wrap(texcoords))
        setIndicesAndCreateBuffer(IntBuffer.wrap(indices))

        this.model = GLMatrix.getIdentity()
        this.model.scale(this.sizes.x(), this.sizes.y(), this.sizes.z())

        return true
    }
}
