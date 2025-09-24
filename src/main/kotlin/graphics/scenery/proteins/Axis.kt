package graphics.scenery.proteins

import graphics.scenery.utils.lazyLogger
import org.joml.Vector3f
import kotlin.math.sqrt

/**
 * Axis class to calculate the axis of a helix and store it as a mathematical line.
 * @param[positions] List of the ca atom positions or respective points on a helix
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class Axis(positions: List<Vector3f?>) {
    val direction = Vector3f()
    val position = Vector3f()
    private val logger by lazyLogger()
    init {
        if(positions.size >= 4) {
            val axisPoints = calculateAxisPoints(positions)
            val line = leastSquare(axisPoints)
            direction.set(line.direction)
            position.set(line.position)
        }
        else {
            logger.warn("Please provide at least four points for the axis calculation.")
        }
    }

    companion object LeastSquares {
        /**
         * Least square method to best approximate the helix axis. The method was developed by Peter C. Kahn ("Simple methods
         * for computing the least square line in three dimensions", Kahn 1989), a detailed mathematical derivation can be
         * found there.
         */
         fun leastSquare(axis: List<Vector3f>): PositionDirection {
            if (axis.isEmpty()) {
                return PositionDirection(Vector3f(), Vector3f())
            } else {
                /*
             Summarizing the calculation, first all the points are translated so that their centroid is in the
             origin of the coordinate system. The following sum is calculated:
             Sx = Σ x*|v'|
             where x is the x component of the original (which means not yet translated) point and v is the
             point vector of the translated point. This is done for y, z respectively. This yields a vector
             s = (Sx, Sy, Sz)
             The direction cosine to the x axis is: Sx/|s|, the same applies to the other axes. Conveniently, the
             vector of all three cosines is the direction vector of the fitted line. The centroid is chosen as the
             positional vector.
             */
                val centroid = getCentroid(axis)
                val transPoints = ArrayList<Pair<Vector3f, Vector3f>>(axis.size)
                axis.forEach { point ->
                    val transPoint = Vector3f()
                    transPoints.add(Pair(point, point.sub(centroid, transPoint)))
                }

                val sumXLength = transPoints.fold(0f) { acc, next -> acc + next.first.x() * next.second.length() }
                val sumYLength = transPoints.fold(0f) { acc, next -> acc + next.first.y() * next.second.length() }
                val sumZLength = transPoints.fold(0f) { acc, next -> acc + next.first.z() * next.second.length() }
                val abs = sqrt(sumXLength * sumXLength + sumYLength * sumYLength + sumZLength * sumZLength)
                val direction = Vector3f(sumXLength / abs, sumYLength / abs, sumZLength / abs)
                return PositionDirection(centroid, direction)
            }
        }

        /**
         * Returns the centroid of a given list of points.
         */
         fun getCentroid(list: List<Vector3f>): Vector3f {
            return Vector3f(list.fold(0f) { acc, next -> acc + next.x() } / list.size,
                    list.fold(0f) { acc, next -> acc + next.y() } / list.size,
                    list.fold(0f) { acc, next -> acc + next.z() } / list.size)
        }
    }

    /**
     * data class to store exactly four ca atom positions.
     */
    data class CaFoursome(val ca1: Vector3f?, val ca2: Vector3f?, val ca3: Vector3f?, val ca4: Vector3f?)

    private fun calculateAxisPoints(caPositions: List<Vector3f?>): ArrayList<Vector3f> {
        if(caPositions.filterNotNull() == caPositions) {
            val allFoursomes = ArrayList<CaFoursome>(caPositions.size - caPositions.size % 3)
            val axis = ArrayList<Vector3f>(allFoursomes.size * allFoursomes.size * 2)
            caPositions.windowed(4, 1) {
                allFoursomes.add(CaFoursome(it[0], it[1], it[2], it[3]))
            }
            allFoursomes.forEach { foursome ->
                val pair = calculateAxis(foursome)
                axis.add(pair.first)
                axis.add(pair.second)
            }
            return axis
        }
        else {
            logger.error("Whoops, your ca-atoms in the axis calculation become null.")
            return ArrayList()
        }
    }

    /**
     * Returns the axis of an alpha helix. Axis Vector corresponds to the direction and Axis Point to the
     * position vector.
     */
    private fun calculateAxis(foursome: CaFoursome): Pair<Vector3f, Vector3f> {
        if(foursome.ca1 != null && foursome.ca2 != null && foursome.ca3 != null && foursome.ca4 != null) {
            /*
            Calculating the direction:
            take four consecutive points (Ca1-Ca4), calculate the midpoint between ca1 and ca3 as well as between
            a2 and ca4; the vector between these midpoints is the axis vector.
             */
            val midpoint1 = Vector3f()
            foursome.ca1.sub(foursome.ca3, midpoint1).mul(0.5f)
            val midpoint2 = Vector3f()
            foursome.ca2.sub(foursome.ca4, midpoint2).mul(0.5f)
            val axis = Vector3f()
            midpoint1.sub(midpoint2, axis).normalize()
            val head = Vector3f()
            axis.mul(2f, head).add(midpoint1, head)
            val tail = Vector3f()
            tail.set(midpoint2)
            return Pair(head, tail)
        }
        else {
            logger.info("Whoops, your ca-atoms in the axis calculation become null.")
            return Pair(Vector3f(), Vector3f())
        }
    }
}
