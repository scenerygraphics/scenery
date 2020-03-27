package graphics.scenery

import cleargl.GLVector

/**
 * This is the abstract class for all the spline classes currently existing. It takes a list
 * of [controlPoints], which constrict spline. Moreover, one has to specify the number of points
 * in one segment of the spline [n].
 *
 * @author Justin Bürger <burger@mpi-cbg.de>
 */
abstract class Spline(open val controlPoints: List<GLVector>, open val n: Int) {
    /**
     * Returns all the points the spline has.
     */
    abstract fun splinePoints(): ArrayList<GLVector>
}
