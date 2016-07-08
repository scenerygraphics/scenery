package scenery

import java.util.*

/**
 * Constructs a sphere with the given [radius] and number of [segments].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @param[radius] The radius of the sphere
 * @param[segments] Number of segments in latitude and longitude.
 */
class Sphere(radius: Float, segments: Int) : Node("sphere"), HasGeometry {
    /** Radius of the sphere. */
    var radius: Float
    /** Segment count in latitude and longitude. */
    var segments: Int

    override val vertexSize = 3;
    override val texcoordSize = 2;
    override val geometryType = GeometryType.TRIANGLE_STRIP;

    override var vertices: FloatArray = floatArrayOf()
    override var normals: FloatArray = floatArrayOf()
    override var texcoords: FloatArray = floatArrayOf()
    override var indices: IntArray = intArrayOf()

    init {
        this.radius = radius
        this.segments = segments

        var vbuffer = ArrayList<Float>()
        var nbuffer = ArrayList<Float>()

        for (i: Int in 1..segments) {
            val lat0: Float = Math.PI.toFloat() * (-0.5f + (i.toFloat() - 1.0f) / segments.toFloat());
            val lat1: Float = Math.PI.toFloat() * (-0.5f + i.toFloat() / segments.toFloat());

            val z0 = Math.sin(lat0.toDouble()).toFloat()
            val z1 = Math.sin(lat1.toDouble()).toFloat()

            val zr0 = Math.cos(lat0.toDouble()).toFloat()
            val zr1 = Math.cos(lat1.toDouble()).toFloat()

            for (j: Int in 1..segments) {
                val lng = 2 * Math.PI.toFloat() * (j - 1) / segments
                val x = Math.cos(lng.toDouble()).toFloat()
                val y = Math.sin(lng.toDouble()).toFloat()

                vbuffer.add(x * zr0 * radius)
                vbuffer.add(y * zr0 * radius)
                vbuffer.add(z0 * radius)

                vbuffer.add(x * zr1 * radius)
                vbuffer.add(y * zr1 * radius)
                vbuffer.add(z1 * radius)

                nbuffer.add(x)
                nbuffer.add(y)
                nbuffer.add(z0)

                nbuffer.add(x)
                nbuffer.add(y)
                nbuffer.add(z1)
            }
        }

        vertices = vbuffer.toFloatArray()
        normals = nbuffer.toFloatArray()
    }

}
