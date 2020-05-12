package graphics.scenery

import org.joml.Vector3f

/**
 * This is the interface for all the spline classes.
 *
 * @author Justin Bürger <burger@mpi-cbg.de>
 */
interface Spline {
    /**
     * Returns all the points the spline has.
     */
    abstract fun splinePoints(): List<Vector3f>
}
