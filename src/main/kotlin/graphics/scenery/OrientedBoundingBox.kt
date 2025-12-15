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

    /** TODO This is a temporary fix for https://github.com/JOML-CI/JOML/issues/379.
     * This code is a copy of JOMLs own testObOb code, but with the rotation matrices fixed (as described in the issue). */
    fun testObOb(
    b0c: Vector3f, b0uX: Vector3f, b0uY: Vector3f, b0uZ: Vector3f, b0hs: Vector3f,
    b1c: Vector3f, b1uX: Vector3f, b1uY: Vector3f, b1uZ: Vector3f, b1hs: Vector3f): Boolean {
        return testObOb(
            b0c.x, b0c.y, b0c.z, b0uX.x, b0uX.y, b0uX.z, b0uY.x, b0uY.y, b0uY.z, b0uZ.x, b0uZ.y, b0uZ.z, b0hs.x, b0hs.y, b0hs.z,
            b1c.x, b1c.y, b1c.z, b1uX.x, b1uX.y, b1uX.z, b1uY.x, b1uY.y, b1uY.z, b1uZ.x, b1uZ.y, b1uZ.z, b1hs.x, b1hs.y, b1hs.z);
    }

    fun testObOb(
        b0cX: Float, b0cY: Float, b0cZ: Float,
        b0uXx: Float, b0uXy: Float, b0uXz: Float,
        b0uYx: Float, b0uYy: Float, b0uYz: Float,
        b0uZx: Float, b0uZy: Float, b0uZz: Float,
        b0hsX: Float, b0hsY: Float, b0hsZ: Float,
        b1cX: Float, b1cY: Float, b1cZ: Float,
        b1uXx: Float, b1uXy: Float, b1uXz: Float,
        b1uYx: Float, b1uYy: Float, b1uYz: Float,
        b1uZx: Float, b1uZy: Float, b1uZz: Float,
        b1hsX: Float, b1hsY: Float, b1hsZ: Float
    ): Boolean {
        var ra: Float
        var rb: Float

        // Compute rotation matrix expressing b in a's coordinate frame (column-major)
        val rm00 = b0uXx * b1uXx + b0uXy * b1uXy + b0uXz * b1uXz;
        val rm01 = b0uXx * b1uYx + b0uXy * b1uYy + b0uXz * b1uYz;
        val rm02 = b0uXx * b1uZx + b0uXy * b1uZy + b0uXz * b1uZz;
        val rm10 = b0uYx * b1uXx + b0uYy * b1uXy + b0uYz * b1uXz;
        val rm11 = b0uYx * b1uYx + b0uYy * b1uYy + b0uYz * b1uYz;
        val rm12 = b0uYx * b1uZx + b0uYy * b1uZy + b0uYz * b1uZz;
        val rm20 = b0uZx * b1uXx + b0uZy * b1uXy + b0uZz * b1uXz;
        val rm21 = b0uZx * b1uYx + b0uZy * b1uYy + b0uZz * b1uYz;
        val rm22 = b0uZx * b1uZx + b0uZy * b1uZy + b0uZz * b1uZz;

        // Compute common subexpressions. Add in an epsilon term to
        // counteract arithmetic errors when two edges are parallel and
        // their cross product is (near) null (see text for details)
        val EPSILON = 1E-5f
        val arm00 = abs(rm00) + EPSILON
        val arm01 = abs(rm01) + EPSILON
        val arm02 = abs(rm02) + EPSILON
        val arm10 = abs(rm10) + EPSILON
        val arm11 = abs(rm11) + EPSILON
        val arm12 = abs(rm12) + EPSILON
        val arm20 = abs(rm20) + EPSILON
        val arm21 = abs(rm21) + EPSILON
        val arm22 = abs(rm22) + EPSILON

        // Compute translation vector t
        val tx = b1cX - b0cX
        val ty = b1cY - b0cY
        val tz = b1cZ - b0cZ

        // Bring translation into a's coordinate frame
        val tax = tx * b0uXx + ty * b0uXy + tz * b0uXz
        val tay = tx * b0uYx + ty * b0uYy + tz * b0uYz
        val taz = tx * b0uZx + ty * b0uZy + tz * b0uZz

        // Test axes L = A0, L = A1, L = A2
        ra = b0hsX
        rb = b1hsX * arm00 + b1hsY * arm01 + b1hsZ * arm02
        if (abs(tax) > ra + rb) return false

        ra = b0hsY
        rb = b1hsX * arm10 + b1hsY * arm11 + b1hsZ * arm12
        if (abs(tay) > ra + rb) return false

        ra = b0hsZ
        rb = b1hsX * arm20 + b1hsY * arm21 + b1hsZ * arm22
        if (abs(taz) > ra + rb) return false

        // Test axes L = B0, L = B1, L = B2
        ra = b0hsX * arm00 + b0hsY * arm10 + b0hsZ * arm20
        rb = b1hsX
        if (abs(tax * rm00 + tay * rm10 + taz * rm20) > ra + rb) return false

        ra = b0hsX * arm01 + b0hsY * arm11 + b0hsZ * arm21
        rb = b1hsY
        if (abs(tax * rm01 + tay * rm11 + taz * rm21) > ra + rb) return false

        ra = b0hsX * arm02 + b0hsY * arm12 + b0hsZ * arm22
        rb = b1hsZ
        if (abs(tax * rm02 + tay * rm12 + taz * rm22) > ra + rb) return false

        // Test axis L = A0 x B0
        ra = b0hsY * arm20 + b0hsZ * arm10
        rb = b1hsY * arm02 + b1hsZ * arm01
        if (abs(taz * rm10 - tay * rm20) > ra + rb) return false

        // Test axis L = A0 x B1
        ra = b0hsY * arm21 + b0hsZ * arm11
        rb = b1hsX * arm02 + b1hsZ * arm00
        if (abs(taz * rm11 - tay * rm21) > ra + rb) return false

        // Test axis L = A0 x B2
        ra = b0hsY * arm22 + b0hsZ * arm12
        rb = b1hsX * arm01 + b1hsY * arm00
        if (abs(taz * rm12 - tay * rm22) > ra + rb) return false

        // Test axis L = A1 x B0
        ra = b0hsX * arm20 + b0hsZ * arm00
        rb = b1hsY * arm12 + b1hsZ * arm11
        if (abs(tax * rm20 - taz * rm00) > ra + rb) return false

        // Test axis L = A1 x B1
        ra = b0hsX * arm21 + b0hsZ * arm01
        rb = b1hsX * arm12 + b1hsZ * arm10
        if (abs(tax * rm21 - taz * rm01) > ra + rb) return false

        // Test axis L = A1 x B2
        ra = b0hsX * arm22 + b0hsZ * arm02
        rb = b1hsX * arm11 + b1hsY * arm10
        if (abs(tax * rm22 - taz * rm02) > ra + rb) return false

        // Test axis L = A2 x B0
        ra = b0hsX * arm10 + b0hsY * arm00
        rb = b1hsY * arm22 + b1hsZ * arm21
        if (abs(tay * rm00 - tax * rm10) > ra + rb) return false

        // Test axis L = A2 x B1
        ra = b0hsX * arm11 + b0hsY * arm01
        rb = b1hsX * arm22 + b1hsZ * arm20
        if (abs(tay * rm01 - tax * rm11) > ra + rb) return false

        // Test axis L = A2 x B2
        ra = b0hsX * arm12 + b0hsY * arm02
        rb = b1hsX * arm21 + b1hsY * arm20
        if (abs(tay * rm02 - tax * rm12) > ra + rb) return false

        // Since no separating axis is found, the OBBs must be intersecting
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
            testObOb(
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
