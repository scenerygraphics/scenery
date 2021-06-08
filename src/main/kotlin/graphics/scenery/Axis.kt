package graphics.scenery

import graphics.scenery.utils.LazyLogger
import org.joml.Vector3f
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue
import kotlin.math.sqrt

/**
 * Axis class to calculate the axis of a helix and store it as a mathematical line.
 * @param[positions] List of the ca atom positions or respective points on a helix
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class Axis(positions: List<Vector3f?>, val scene: Scene? = null) {
    val direction = Vector3f()
    val position = Vector3f()
    private val logger by LazyLogger()
    init {
        print("here we go, ${positions.size}")
        if(positions.size >= 4) {
            val axisPoints = calculateAxisPoints(positions)
            val line = leastSquare(axisPoints.first, axisPoints.second)
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
         fun leastSquare(axisDir: List<Vector3f>, axisPoint: List<Vector3f>): MathLine {
            if (axisDir.isEmpty()) {
                return MathLine(Vector3f(), Vector3f())
            } else {
                /*
                The centroid as the axis point is derived with another method which does deliver points lying more central
                in the helix.
                 */
                val centroid = getCentroid(axisPoint)
                    /*
                 Summarizing the calculation: first all the points are translated so that their centroid is in the
                 origin of the coordinate system. The following sum is calculated:
                 Sx = Σ x were x is the x component of the translated data point. This yields a vector
                 s = (Sx, Sy, Sz)
                 The direction cosine to the x axis is: Sx/|s|, the same applies to the other axes. Conveniently, the
                 vector of all three cosines is the direction vector of the fitted line. The centroid is chosen as the
                 positional vector.
                 */
                val dirCentroid = getCentroid(axisDir)
                val transPoints = ArrayList<Vector3f>(axisDir.size)
                axisDir.map { point ->
                    val transPoint = Vector3f()
                    transPoints.add(point.sub(dirCentroid, transPoint))
                }
                val axisPoints = ArrayList<Vector3f>(transPoints.size/2)
                transPoints.windowed(2, 2) {
                    val partAxis = Vector3f()
                    it[0].sub(it[1], partAxis).normalize()
                    axisPoints.add(partAxis)
                }
                val sumXLength = axisPoints.fold(0f) { acc, next -> acc + next.x() }
                val sumYLength = axisPoints.fold(0f) { acc, next -> acc + next.y() }
                val sumZLength = axisPoints.fold(0f) { acc, next -> acc + next.z() }
                val abs = sqrt(sumXLength * sumXLength + sumYLength * sumYLength + sumZLength * sumZLength)
                val direction = Vector3f(sumXLength / abs, sumYLength / abs, sumZLength / abs)
                return MathLine(direction, centroid)
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

    private fun calculateAxisPoints(caPositions: List<Vector3f?>): Pair<ArrayList<Vector3f>, ArrayList<Vector3f>> {
        if(caPositions.filterNotNull() == caPositions) {
            val allFoursomes = ArrayList<CaFoursome>(caPositions.size - caPositions.size % 3)
            val axisDir = ArrayList<Vector3f>(allFoursomes.size * allFoursomes.size * 2)
            val axisPoint = ArrayList<Vector3f>(allFoursomes.size * allFoursomes.size * 2)
            caPositions.windowed(4, 1) {
                allFoursomes.add(CaFoursome(it[0], it[1], it[2], it[3]))
            }
            allFoursomes.forEach { foursome ->
                val twoPairs = calculateAxis(foursome)
                axisDir.add(twoPairs.directionPoint1)
                axisDir.add(twoPairs.directionPoint2)
                axisPoint.add(twoPairs.pointVec1)
                axisPoint.add(twoPairs.pointVec2)
            }
            return Pair(axisDir, axisPoint)
        }
        else {
            logger.error("Whoops, your ca-atoms in the axis calculation become null.")
            return Pair(ArrayList(), java.util.ArrayList())
        }
    }

    /**
     * Returns the axis of an alpha helix. Axis Vector corresponds to the direction and Axis Point to the
     * position vector.
     */
    private fun calculateAxis(foursome: CaFoursome): DirectionAndPointPair {
        if(foursome.ca1 != null && foursome.ca2 != null && foursome.ca3 != null && foursome.ca4 != null) {

             /* Kahns Method delivers a better direction vector, however, the positional vector is not very precise.
             Therefore, we use the midpoints between ca atoms, see below.
             */
            val p1 = Vector3f()
            p1.set(foursome.ca2)
            val p2 = Vector3f()
            p2.set(foursome.ca3)
            val a1 = Vector3f()
            p1.sub(foursome.ca1, a1)
            val b1 = Vector3f()
            p1.sub(foursome.ca3, b1)
            val a2 = Vector3f()
            p2.sub(foursome.ca2, a2)
            val b2 = Vector3f()
            p2.sub(foursome.ca4, b2)
            val v1 = Vector3f()
            a1.add(b1, v1).normalize()
            val v2 = Vector3f()
            a2.add(b2, v2).normalize()
            val h = Vector3f()
            v1.cross(v2, h).normalize()
            val p2Subp1 = Vector3f()
            p2.sub(p1, p2Subp1)
            val d = p2Subp1.dot(h)
            val hMuld = Vector3f()
            val hMuldAbs = h.mul(d, hMuld).length()
            val p1subp2 = Vector3f()
            p2Subp1.mul(-1f, p1subp2)
            val r = (hMuldAbs*hMuldAbs - p2Subp1.length()*p2Subp1.length())/(2f*(p1subp2.dot(v2).absoluteValue)).absoluteValue
            val h1 = Vector3f()
            p1.add((v1.mul(r, h1)), h1)
            val h2 = Vector3f()
            p2.add((v2.mul(r, h2)), h2)
            val axis = Vector3f()
            h2.sub(h1, axis)

            /*
           Calculating the positional vector:
           take four consecutive points (Ca1-Ca4), calculate the midpoint between ca1 and ca3 as well as between
           a2 and ca4; forming the centroid of all resulting vectors should yield a reasonable approximation of
           the positional vector.
            */
            val midpoint1 = Vector3f()
            foursome.ca1.add(foursome.ca3, midpoint1).mul(0.5f)
            val midpoint2 = Vector3f()
            foursome.ca2.add(foursome.ca4, midpoint2).mul(0.5f)
            val axis1 = Vector3f()
            midpoint2.sub(midpoint1, axis1).normalize()


            return DirectionAndPointPair(h1, h2, midpoint1, midpoint2)
        }
        else {
            logger.info("Whoops, your ca-atoms in the axis calculation become null.")
            return DirectionAndPointPair(Vector3f(), Vector3f(), Vector3f(), Vector3f())
        }
    }

    /**
     * Class which stores the four axisPoints calculated: two for the direction, two for the points
     */
    data class DirectionAndPointPair(val directionPoint1: Vector3f, val directionPoint2: Vector3f, val pointVec1: Vector3f,
                                     val pointVec2: Vector3f)
}
