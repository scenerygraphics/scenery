package graphics.scenery

import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Intersectionf
import org.joml.Vector3f
import java.lang.Math.max
import java.lang.Math.min

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
            Intersectionf.testObOb(
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
