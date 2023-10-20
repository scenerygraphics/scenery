package graphics.scenery.geometry.curve

import graphics.scenery.Mesh
import graphics.scenery.geometry.Spline
import org.joml.Vector3f

/**
 * Constructs a geometry along the calculates points of a Spline.
 * The number n corresponds to the number of segments you wish to have between your control points.
 * The spline and the baseShape lambda must both have the same number of elements, otherwise, the curve is no
 * longer well-defined. Concerning the individual baseShapes, no lines must cross for the body of the curve to
 * be visualized flawlessly.
 *
 * In this implementation, for each section of a curve (for every spline point a section is created) an individual mesh
 * is added to [this] class as a child.
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 * @param [baseShape] a lambda which returns all the baseShapes along the curve
 * @param [spline] the spline along which the geometry will be rendered
 */
class DefaultCurve(override val spline: Spline,
                   override val baseShapes: () -> List<List<Vector3f>>,
                   override val firstPerpendicularVector3f: Vector3f= Vector3f(0f, 0f, 0f),
): FrenetOrientedCurve, Mesh("DefaultCurve") {

    override val frenetFrames: () -> List<FrenetFrame> =
        { FrenetFrameCalculation.computeFrenetFrames(spline, firstPerpendicularVector3f) }
    private val geometryCalculator = CurveGeometryCalculation
    init {
        val pointsPerSection = spline.pointsPerSection()
        val transformedBaseShapes = transformedBaseShapes(baseShapes.invoke(), frenetFrames.invoke())

        //No partialWindow here as we add the remaining points below
        val subShapes = transformedBaseShapes.windowed(pointsPerSection+1, pointsPerSection, partialWindows = false)

        subShapes.forEachIndexed { index, listShapes ->
            val cover = when (index) {
                0 -> {
                    CurveCover.Top
                }

                subShapes.lastIndex -> {
                    CurveCover.Bottom
                }

                else -> {
                    CurveCover.None
                }
            }
            //default behaviour: the last, partial section (if it exists) will be added to the last window of the shapes
            if(index == subShapes.lastIndex) {
                val arrayListShapes = listShapes as ArrayList
                for(i in (transformedBaseShapes.size % pointsPerSection) downTo 1) {
                    arrayListShapes.add(transformedBaseShapes[transformedBaseShapes.lastIndex -i + 1])
                }
                val trianglesAndNormals = geometryCalculator.calculateTriangles(arrayListShapes, cover = cover)
                val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
                this.addChild(partialCurve)
            }
            else {
                val trianglesAndNormals = geometryCalculator.calculateTriangles(listShapes, cover = cover)
                val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
                this.addChild(partialCurve)
            }
        }
    }
}
