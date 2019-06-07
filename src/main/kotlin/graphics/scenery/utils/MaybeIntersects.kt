package graphics.scenery.utils

import cleargl.GLVector

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
                       val entry: GLVector,
                       val exit: GLVector,
                       val relativeEntry: GLVector,
                       val relativeExit: GLVector) : MaybeIntersects(true)
}
