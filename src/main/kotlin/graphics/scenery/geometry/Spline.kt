package graphics.scenery.geometry

import org.joml.Vector3f

/**
 * This is the interface for all the spline classes.
 *
 * @author Justin Bürger <burger@mpi-cbg.de>
 */
interface Spline {

    /**
     * Returns the control points of the spline. We decided to make it a function, so the control
     * points are not mutable to prevent errors.
     */
    fun controlPoints(): List<Vector3f>

    /**
     * Returns the spline as a list of points. Please note that this is supposed to be the final
     * spline because the Curve takes this very function and builds a geometry around its
     * points.
     */
    fun splinePoints(): List<Vector3f>

    /**
     * Returns the number of points the spline contains in each section.
     */
    fun pointsPerSection(): Int
}
