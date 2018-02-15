package graphics.scenery

import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Constructs a sphere with the given [radius] and number of [segments].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @param[radius] The radius of the sphere
 * @param[segments] Number of segments in latitude and longitude.
 */
open class Sphere(val radius: Float, val segments: Int) : Node("sphere"), HasGeometry {
    override val vertexSize = 3
    override val texcoordSize = 2
    override var geometryType = GeometryType.TRIANGLE_STRIP

    override var vertices: FloatBuffer = BufferUtils.allocateFloat(0)
    override var normals: FloatBuffer = BufferUtils.allocateFloat(0)
    override var texcoords: FloatBuffer = BufferUtils.allocateFloat(0)
    override var indices: IntBuffer = BufferUtils.allocateInt(0)

    init {
        val vbuffer = ArrayList<Float>(segments*segments*2*3)
        val nbuffer = ArrayList<Float>(segments*segments*2*3)
        val tbuffer = ArrayList<Float>(segments*segments*2*2)

        for (i: Int in 0..segments) {
            val lat0: Float = PI.toFloat() * (-0.5f + (i-1.0f) / segments)
            val lat1: Float = PI.toFloat() * (-0.5f + 1.0f*i / segments)

            val z0: Float = sin(lat0)
            val z1: Float = sin(lat1)

            val zr0: Float = cos(lat0)
            val zr1: Float = cos(lat1)

            for (j: Int in 0..segments) {
                val lng: Float = 2 * PI.toFloat() * (j - 1) / segments
                val x: Float = cos(lng)
                val y: Float = sin(lng)

                vbuffer.add(x * zr1 * radius)
                vbuffer.add(y * zr1 * radius)
                vbuffer.add(z1 * radius)

                vbuffer.add(x * zr0 * radius)
                vbuffer.add(y * zr0 * radius)
                vbuffer.add(z0 * radius)

                nbuffer.add(x)
                nbuffer.add(y)
                nbuffer.add(z1)

                nbuffer.add(x)
                nbuffer.add(y)
                nbuffer.add(z0)

                tbuffer.add(0.0f)
                tbuffer.add(0.0f)

                tbuffer.add(0.0f)
                tbuffer.add(0.0f)
            }
        }

        vertices = BufferUtils.allocateFloatAndPut(vbuffer.toFloatArray())
        normals = BufferUtils.allocateFloatAndPut(nbuffer.toFloatArray())
        texcoords = BufferUtils.allocateFloatAndPut(tbuffer.toFloatArray())
    }

}
