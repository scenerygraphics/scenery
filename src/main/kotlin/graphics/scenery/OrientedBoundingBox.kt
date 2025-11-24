package graphics.scenery

import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Intersectionf
import org.joml.Vector3f
import java.lang.Math.max
import java.lang.Math.min
import kotlin.math.abs

/**
 * Oriented bounding box class to perform easy intersection tests.
 *
 * @property[min] The x/y/z minima for the bounding box.
 * @property[max] The x/y/z maxima for the bounding box.
 */
open class OrientedBoundingBox(val n: Node, val min: Vector3f, val max: Vector3f) {
    /**
     * Bounding sphere class, a bounding sphere is defined by an origin and a radius,
     * to enclose all of the Node's geometry.
     */
    data class BoundingSphere(val origin: Vector3f, val radius: Float)

    /**
     * Alternative [OrientedBoundingBox] constructor taking the [min] and [max] as a series of floats.
     */
    constructor(n: Node, xMin: Float, yMin: Float, zMin: Float, xMax: Float, yMax: Float, zMax: Float) : this(n, Vector3f(xMin, yMin, zMin), Vector3f(xMax, yMax, zMax))

    /**
     * Alternative [OrientedBoundingBox] constructor, taking a 6-element float array for [min] and [max].
     */
    constructor(n: Node, boundingBox: FloatArray) : this(n, Vector3f(boundingBox[0], boundingBox[2], boundingBox[4]), Vector3f(boundingBox[1], boundingBox[3], boundingBox[5]))

    val center: Vector3f
        get() {
            val worldMin = n.spatialOrNull()!!.worldPosition(min)
            val worldMax = n.spatialOrNull()!!.worldPosition(max)

            return worldMin + (worldMax - worldMin) * 0.5f
        }

    val halfSize: Vector3f
        get() {
            val scale = Vector3f()
            n.spatialOrNull()!!.world.getScale(scale)
            return ((max - min) * 0.5f) * scale
        }

    /**
     * Returns the maximum bounding sphere of this bounding box.
     */
    fun getBoundingSphere(): BoundingSphere {
        var origin = Vector3f(0f, 0f, 0f)
        var radius = 0f
        n.ifSpatial {
            if(needsUpdate || needsUpdateWorld) {
                updateWorld(true, false)
            }

            val worldMin = worldPosition(min)
            val worldMax = worldPosition(max)

            origin = worldMin + (worldMax - worldMin) * 0.5f
            radius = (worldMax - origin).length()
        }

        return BoundingSphere(origin, radius)
    }

    fun testObbIntersection(
        b0c: Vector3f, b0uX: Vector3f, b0uY: Vector3f, b0uZ: Vector3f, b0hs: Vector3f,
        b1c: Vector3f, b1uX: Vector3f, b1uY: Vector3f, b1uZ: Vector3f, b1hs: Vector3f
    ): Boolean {
        // Vector from box 0 center to box 1 center
        val t = Vector3f(b1c).sub(b0c)

        // Rotation matrix expressing box 1 in box 0's coordinate frame
        val r = Array(3) { FloatArray(3) }
        val absR = Array(3) { FloatArray(3) }

        val b0Axes = arrayOf(b0uX, b0uY, b0uZ)
        val b1Axes = arrayOf(b1uX, b1uY, b1uZ)

        // Compute rotation matrix and absolute rotation matrix
        for (i in 0..2) {
            for (j in 0..2) {
                r[i][j] = b0Axes[i].dot(b1Axes[j])
                absR[i][j] = abs(r[i][j]) + 1e-6f // Add epsilon to counteract arithmetic errors
            }
        }

        val b0HalfSizes = floatArrayOf(b0hs.x, b0hs.y, b0hs.z)
        val b1HalfSizes = floatArrayOf(b1hs.x, b1hs.y, b1hs.z)

        // Test axes L = A0, A1, A2 (box 0's axes)
        for (i in 0..2) {
            val tProj = t.dot(b0Axes[i])
            val r0 = b0HalfSizes[i]
            val r1 = b1HalfSizes[0] * absR[i][0] + b1HalfSizes[1] * absR[i][1] + b1HalfSizes[2] * absR[i][2]
            if (abs(tProj) > r0 + r1) return false
        }

        // Test axes L = B0, B1, B2 (box 1's axes)
        for (i in 0..2) {
            val tProj = t.dot(b1Axes[i])
            val r0 = b0HalfSizes[0] * absR[0][i] + b0HalfSizes[1] * absR[1][i] + b0HalfSizes[2] * absR[2][i]
            val r1 = b1HalfSizes[i]
            if (abs(tProj) > r0 + r1) return false
        }

        // Test 9 cross product axes (A x B)

        // L = A0 x B0
        var tProj = t.z * r[1][0] - t.y * r[2][0]
        var r0 = b0HalfSizes[1] * absR[2][0] + b0HalfSizes[2] * absR[1][0]
        var r1 = b1HalfSizes[1] * absR[0][2] + b1HalfSizes[2] * absR[0][1]
        if (abs(tProj) > r0 + r1) return false

        // L = A0 x B1
        tProj = t.z * r[1][1] - t.y * r[2][1]
        r0 = b0HalfSizes[1] * absR[2][1] + b0HalfSizes[2] * absR[1][1]
        r1 = b1HalfSizes[0] * absR[0][2] + b1HalfSizes[2] * absR[0][0]
        if (abs(tProj) > r0 + r1) return false

        // L = A0 x B2
        tProj = t.z * r[1][2] - t.y * r[2][2]
        r0 = b0HalfSizes[1] * absR[2][2] + b0HalfSizes[2] * absR[1][2]
        r1 = b1HalfSizes[0] * absR[0][1] + b1HalfSizes[1] * absR[0][0]
        if (abs(tProj) > r0 + r1) return false

        // L = A1 x B0
        tProj = t.x * r[2][0] - t.z * r[0][0]
        r0 = b0HalfSizes[0] * absR[2][0] + b0HalfSizes[2] * absR[0][0]
        r1 = b1HalfSizes[1] * absR[1][2] + b1HalfSizes[2] * absR[1][1]
        if (abs(tProj) > r0 + r1) return false

        // L = A1 x B1
        tProj = t.x * r[2][1] - t.z * r[0][1]
        r0 = b0HalfSizes[0] * absR[2][1] + b0HalfSizes[2] * absR[0][1]
        r1 = b1HalfSizes[0] * absR[1][2] + b1HalfSizes[2] * absR[1][0]
        if (abs(tProj) > r0 + r1) return false

        // L = A1 x B2
        tProj = t.x * r[2][2] - t.z * r[0][2]
        r0 = b0HalfSizes[0] * absR[2][2] + b0HalfSizes[2] * absR[0][2]
        r1 = b1HalfSizes[0] * absR[1][1] + b1HalfSizes[1] * absR[1][0]
        if (abs(tProj) > r0 + r1) return false

        // L = A2 x B0
        tProj = t.y * r[0][0] - t.x * r[1][0]
        r0 = b0HalfSizes[0] * absR[1][0] + b0HalfSizes[1] * absR[0][0]
        r1 = b1HalfSizes[1] * absR[2][2] + b1HalfSizes[2] * absR[2][1]
        if (abs(tProj) > r0 + r1) return false

        // L = A2 x B1
        tProj = t.y * r[0][1] - t.x * r[1][1]
        r0 = b0HalfSizes[0] * absR[1][1] + b0HalfSizes[1] * absR[0][1]
        r1 = b1HalfSizes[0] * absR[2][2] + b1HalfSizes[2] * absR[2][0]
        if (abs(tProj) > r0 + r1) return false

        // L = A2 x B2
        tProj = t.y * r[0][2] - t.x * r[1][2]
        r0 = b0HalfSizes[0] * absR[1][2] + b0HalfSizes[1] * absR[0][2]
        r1 = b1HalfSizes[0] * absR[2][1] + b1HalfSizes[1] * absR[2][0]
        if (abs(tProj) > r0 + r1) return false

        // No separating axis found - boxes intersect
        return true
    }

    /**
     * Checks this [OrientedBoundingBox] for intersection with [other], and returns
     * true if the bounding boxes do intersect.
     *
     * If [precise] is true, the intersection test will still test with the less precise bounding sphere test first,
     * and if it returns true a more precise test will be performed using oriented bounding boxes (OBBs).
     */
    @JvmOverloads
    fun intersects(other: OrientedBoundingBox, precise: Boolean = false): Boolean {
        val approxResult =
            other.getBoundingSphere().radius + getBoundingSphere().radius > (other.getBoundingSphere().origin - getBoundingSphere().origin).length()
        return if(precise && approxResult) {
            testObbIntersection(
                this.center,
                this.n.spatialOrNull()!!.localX,
                this.n.spatialOrNull()!!.localY,
                this.n.spatialOrNull()!!.localZ,
                this.halfSize,
                other.center,
                other.n.spatialOrNull()!!.localX,
                other.n.spatialOrNull()!!.localY,
                other.n.spatialOrNull()!!.localZ,
                other.halfSize
            )
        } else {
            return approxResult
        }
    }

    /**
     * Checks whether a [point] is inside the given [OrientedBoundingBox] or not.
     */
    fun isInside(point: Vector3f): Boolean {
        return (point.x > min.x && point.y > min.y && point.z > min.z &&
                point.x < max.x && point.y < max.y && point.z < max.z)
    }

    /**
     * Returns the hash code of this [OrientedBoundingBox], taking [min] and [max] into consideration.
     */
    override fun hashCode(): Int {
        return min.hashCode() + max.hashCode()
    }

    /**
     * Compares this bounding box to [other], returning true if they are equal.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as? OrientedBoundingBox ?: return false

        if (min.hashCode() != other.min.hashCode()) return false
        if (max.hashCode() != other.max.hashCode()) return false

        return true
    }

    /**
     * Return an [OrientedBoundingBox] that covers both [lhs] and [rhs].
     */
    fun expand(lhs: OrientedBoundingBox, rhs: OrientedBoundingBox): OrientedBoundingBox {
        return OrientedBoundingBox(lhs.n,
            min(lhs.min.x(), rhs.min.x()),
            min(lhs.min.y(), rhs.min.y()),
            min(lhs.min.z(), rhs.min.z()),
            max(lhs.max.x(), rhs.max.x()),
            max(lhs.max.y(), rhs.max.y()),
            max(lhs.max.z(), rhs.max.z()))
    }

    /**
     * Return an [OrientedBoundingBox] in World coordinates.
     */
    fun asWorld(): OrientedBoundingBox {
        return OrientedBoundingBox(n,
            n.spatialOrNull()?.worldPosition(min) ?: Vector3f(0.0f, 0.0f, 0.0f),
            n.spatialOrNull()?.worldPosition(max)?: Vector3f(0.0f, 0.0f, 0.0f))
    }

    /**
     * Return an [OrientedBoundingBox] with min/max translated by offset vector.
     */
    fun translate(offset: Vector3f): OrientedBoundingBox {
        return OrientedBoundingBox(n, min + offset, max + offset)
    }

    override fun toString(): String {
        return "OrientedBoundingBox(min=$min, max=$max)"
    }
}
