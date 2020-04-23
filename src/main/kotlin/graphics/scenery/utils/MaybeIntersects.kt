package graphics.scenery.utils

import org.joml.Vector3f

/**
 * Sealed class to represent intersection states:
 * NoIntersection should be pretty clear, Intersection encapsulates an actual
 * intersection and stores distance, entry and exit points, as well as the bounding box-relative
 * entry and exit points.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
sealed class MaybeIntersects(val intersects: Boolean) {
    /**
     * No intersection has been found.
     */
    class NoIntersection : MaybeIntersects(false)

    /**
     * Intersection has been found at [distance], with [entry] and [exit] points, and
     * [relativeEntry] and [relativeExit] points.
     */
    class Intersection(val distance: Float,
                       val entry: Vector3f,
                       val exit: Vector3f,
                       val relativeEntry: Vector3f,
                       val relativeExit: Vector3f) : MaybeIntersects(true)
}
