package graphics.scenery

import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*

/**
 * Constructs a cylinder with the given [radius] and number of [segments].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @param[radius] The radius of the sphere
 * @param[segments] Number of segments in latitude and longitude.
 */

class Cone(var radius: Float, var height: Float, var segments: Int) : Node("cone"), HasGeometry {
    override val vertexSize = 3
    override val texcoordSize = 2
    override val geometryType = GeometryType.TRIANGLE_STRIP

    override var vertices: FloatBuffer = BufferUtils.allocateFloat(0)
    override var normals: FloatBuffer = BufferUtils.allocateFloat(0)
    override var texcoords: FloatBuffer = BufferUtils.allocateFloat(0)
    override var indices: IntBuffer = BufferUtils.allocateInt(0)

    init {
        var vbuffer = ArrayList<Float>(segments * segments * 2 * 3)
        var nbuffer = ArrayList<Float>(segments * segments * 2 * 3)
        var tbuffer = ArrayList<Float>(segments * segments * 2 * 2)

        val delta = 2.0f * Math.PI.toFloat() / segments.toFloat()
        val c = Math.cos(delta * 1.0).toFloat()
        val s = Math.sin(delta * 1.0).toFloat()

        var x2 = radius
        var z2 = 0.0f

        for (i: Int in 0..segments) {
            val texcoord = i / segments.toFloat()
            val normal = 1.0f / Math.sqrt(x2 * x2 * 1.0 + z2 * z2 * 1.0).toFloat()
            val xn = x2 * normal
            val zn = z2 * normal

            nbuffer.add(xn)
            nbuffer.add(0.0f)
            nbuffer.add(zn)

            tbuffer.add(texcoord)
            tbuffer.add(0.0f)

            vbuffer.add(0.0f + x2)
            vbuffer.add(0.0f)
            vbuffer.add(0.0f + z2)

            nbuffer.add(xn)
            nbuffer.add(0.0f)
            nbuffer.add(zn)

            tbuffer.add(texcoord)
            tbuffer.add(1.0f)

            vbuffer.add(0.0f)
            vbuffer.add(0.0f + height)
            vbuffer.add(0.0f)

            val x3 = x2
            x2 = c * x2 - s * z2
            z2 = s * x3 + c * z2
        }

        vertices = BufferUtils.allocateFloatAndPut(vbuffer.toFloatArray())
        normals = BufferUtils.allocateFloatAndPut(nbuffer.toFloatArray())
        texcoords = BufferUtils.allocateFloatAndPut(tbuffer.toFloatArray())
    }

}
