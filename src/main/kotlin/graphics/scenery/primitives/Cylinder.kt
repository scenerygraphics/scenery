package graphics.scenery.primitives

import graphics.scenery.BufferUtils
import graphics.scenery.Mesh
import graphics.scenery.geometry.GeometryType
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.collections.ArrayList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Constructs a cylinder with the given [radius] and number of [segments].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @param[radius] The radius of the sphere
 * @param[segments] Number of segments in latitude and longitude.
 */

open class Cylinder @JvmOverloads constructor(var radius: Float, var height: Float, var segments: Int, fillCaps: Boolean = false, smoothSides: Boolean = false) : Mesh("cylinder") {
    init {
        geometry {
            geometryType = GeometryType.TRIANGLES

            val vBuffer = ArrayList<Float>(segments * 12) // 12 = number of vertices per segment
            val nBuffer = ArrayList<Float>(segments * 12)
            val tBuffer = ArrayList<Float>(segments * 12)

            val delta = 2.0f * PI.toFloat() / segments.toFloat()

            val vTop = Vector3f(0f, height, 0f)
            val vBottom = Vector3f(0f, 0f, 0f)

            for (i: Int in 0 until segments) {
                val theta = i * delta
                val theta1 = (i + 1) * delta
                val texcoord = i / segments.toFloat()
                val texcoord1 = (i + 1) / segments.toFloat()
                val x = radius * cos(theta)
                val x1 = radius * cos(theta1)
                val z = radius * sin(theta)
                val z1 = radius * sin(theta1)

                // 4 vertices per segment
                val v1 = Vector3f(x, 0f, z)
                val v2 = Vector3f(x1, 0f, z1)
                val v3 = Vector3f(x1, height, z1)
                val v4 = Vector3f(x, height, z)

                val n1 = Vector3f(x, 0f, z).normalize()
                val n2 = if (smoothSides) Vector3f(x1, 0f, z1).normalize() else n1
                val nTop = Vector3f(0f, 1f, 0f)
                val nBottom = Vector3f(0f, -1f, 0f)

                val t1 = Vector2f(texcoord, 0f)
                val t2 = Vector2f(texcoord1, 0f)
                val t3 = Vector2f(texcoord1, 1f)
                val t4 = Vector2f(texcoord, 1f)
                val tCenter = Vector2f((texcoord+texcoord1)/2, 1f)

                // Face 1
                putAll(vBuffer, v1, v4, v3)
                putAll(nBuffer, n1, n1, n2)
                putAll(tBuffer, t1, t4, t3)

                // Face 2
                putAll(vBuffer, v1, v3, v2)
                putAll(nBuffer, n1, n2, n2)
                putAll(tBuffer, t1, t3, t2)

                if (fillCaps) {
                    // Face top
                    putAll(vBuffer, v4, vTop, v3)
                    putAll(nBuffer, nTop, nTop, nTop)
                    putAll(tBuffer, t1, tCenter, t2)

                    // Face bottom
                    putAll(vBuffer, v2, vBottom, v1)
                    putAll(nBuffer, nBottom, nBottom, nBottom)
                    putAll(tBuffer, t2, tCenter, t1)
                }
            }

            vertices = BufferUtils.allocateFloatAndPut(vBuffer.toFloatArray())
            normals = BufferUtils.allocateFloatAndPut(nBuffer.toFloatArray())
            texcoords = BufferUtils.allocateFloatAndPut(tBuffer.toFloatArray())
        }

        boundingBox = generateBoundingBox()
    }

    // add 3D coordinates to buffer
    private fun putAll(buffer: ArrayList<Float>, vararg vectors: Vector3f) {
        for (v in vectors) {
            buffer.add(v.x)
            buffer.add(v.y)
            buffer.add(v.z)
        }
    }

    // add 2D coordinates to buffer
    private fun putAll(buffer: ArrayList<Float>, vararg vectors: Vector2f) {
        for (v in vectors) {
            buffer.add(v.x)
            buffer.add(v.y)
        }
    }

    companion object {
        @JvmStatic fun betweenPoints(p1: Vector3f, p2: Vector3f, radius: Float = 0.02f, height: Float = 1.0f, segments: Int = 16): Cylinder {
            val cylinder = Cylinder(radius, height, segments)
            cylinder.spatial {
                orientBetweenPoints(p1, p2, rescale = true, reposition = true)
            }
            return cylinder
        }
    }

}
