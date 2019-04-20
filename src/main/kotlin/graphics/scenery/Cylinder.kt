package graphics.scenery

import cleargl.GLVector
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import kotlin.math.PI

/**
 * Constructs a cylinder with the given [radius] and number of [segments].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @param[radius] The radius of the sphere
 * @param[segments] Number of segments in latitude and longitude.
 */

class Cylinder(var radius: Float, var height: Float, var segments: Int) : Node("cylinder"), HasGeometry {
    override val vertexSize = 3
    override val texcoordSize = 2
    override var geometryType = GeometryType.TRIANGLE_STRIP

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

            vbuffer.add(0.0f + x2)
            vbuffer.add(0.0f + height)
            vbuffer.add(0.0f + z2)

            val x3 = x2
            x2 = c * x2 - s * z2
            z2 = s * x3 + c * z2
        }

        vertices = BufferUtils.allocateFloatAndPut(vbuffer.toFloatArray())
        normals = BufferUtils.allocateFloatAndPut(nbuffer.toFloatArray())
        texcoords = BufferUtils.allocateFloatAndPut(tbuffer.toFloatArray())

        boundingBox = generateBoundingBox()
    }

    companion object {
        @JvmStatic fun betweenPoints(p1: GLVector, p2: GLVector, radius: Float = 0.02f, height: Float = 1.0f, segments: Int = 16): Cylinder {
            val cylinder = Cylinder(radius, height, segments)
            val direction = p2 - p1
            cylinder.rotation = cylinder.rotation
                .setLookAt(direction.normalized.toFloatArray(),
                    floatArrayOf(0.0f, 1.0f, 0.0f),
                    FloatArray(3), FloatArray(3), FloatArray(3))
                .rotateByAngleX(PI.toFloat()/2.0f)
            cylinder.scale = GLVector(1.0f, direction.magnitude(), 1.0f)
            cylinder.position = p1.clone()

            return cylinder
        }
    }

}
