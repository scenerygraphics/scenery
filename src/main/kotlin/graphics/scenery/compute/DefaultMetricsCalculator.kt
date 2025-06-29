package graphics.scenery.compute

import graphics.scenery.geometry.Spline
import org.joml.minus

class DefaultMetricsCalculator(spline: Spline) : SplineMetricsCalculator(spline) {

    /**
     * Curvature as angle between the two neighbouring line segments normalized by the length of the segment between the two points.
     *
     * @param padding If true, adds a 0 at the start and end of the curvature list to match the size of spline points.
     * @return List of curvature values for each point in the spline except for the first and last.
     * */
    override fun curvature(padding: Boolean): ArrayList<Float> {
        val curvatureList = ArrayList<Float>()
        // Padding at start for i = 0
        if(padding){curvatureList.add(0f)}

        val splinePoints = spline.splinePoints()
        for(i in 1 until splinePoints.size - 1) {
            val v1 = splinePoints[i] - splinePoints[i - 1]
            val v2 = splinePoints[i + 1] - splinePoints[i]
            val curvature = v1.angle(v2) / (splinePoints[i+1]-splinePoints[i-1]).length()
            curvatureList.add(curvature)
        }
        // Padding at end for i = last
        if(padding){curvatureList.add(0f)}

        return curvatureList
    }

    /**
     * Menger curvature: "reciprocal of the radius of the circle that passes through the three points"
     * (https://en.wikipedia.org/wiki/Menger_curvature)
     *
     * @param padding If true, adds a 0 at the start and end of the curvature list to match the size of spline points.
     * @return List of Menger curvature values for each point in the spline except for the first and last.
     * */
    fun mengerCurvature(padding: Boolean): ArrayList<Float> {
        val mengerCurvatureList = ArrayList<Float>()
        val splinePoints = spline.splinePoints()

        // Padding at start for i = 0
        if(padding){mengerCurvatureList.add(0f)}

        for(i in 1 until splinePoints.size - 1) {
            val p0 = splinePoints[i - 1]
            val p1 = splinePoints[i]
            val p2 = splinePoints[i + 1]

            val a = (p1 - p0).length()
            val b = (p2 - p1).length()
            val c = (p2 - p0).length()

            val s = (a + b + c) / 2f
            val area = kotlin.math.sqrt(s * (s - a) * (s - b) * (s - c))
            val mengerCurvature = if (area > 0f) 4f * area / (a * b * c) else 0f
            mengerCurvatureList.add(mengerCurvature)
        }

        // Padding at end for i = last
        if(padding){mengerCurvatureList.add(0f)}

        return mengerCurvatureList
    }
}
