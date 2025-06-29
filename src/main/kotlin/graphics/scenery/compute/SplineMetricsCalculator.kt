package graphics.scenery.compute

import graphics.scenery.geometry.Spline

/**
 * Handles the computation of various metrics for a spline.
 * */
abstract class SplineMetricsCalculator(var spline: Spline) {
    abstract fun curvature(padding: Boolean = true): List<Float>
    //open fun arcLength(): Float = TODO()
    //open fun torsion(): Float =TODO()
}
