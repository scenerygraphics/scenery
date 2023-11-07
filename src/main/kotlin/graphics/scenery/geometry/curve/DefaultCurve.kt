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
        val subShapes = transformedBaseShapes.windowed(pointsPerSection+1, pointsPerSection, partialWindows = true)

        subShapes.forEachIndexed { index, listShapes ->
            //if the last section contains only a single shape, it will be added below
            if (listShapes.size > 1) {
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
                //default behaviour: if the last section is a single shape,
                // it will be added to the last window of the shapes
                if (index == subShapes.lastIndex - 1 && subShapes.last().size == 1) {
                    val arrayListShapes = listShapes as ArrayList
                    arrayListShapes.add(transformedBaseShapes.last())
                    val trianglesAndNormals = geometryCalculator.calculateTriangles(arrayListShapes,
                        cover = CurveCover.Bottom)
                    val partialCurveMesh = PartialCurveMesh(trianglesAndNormals.first, trianglesAndNormals.second)
                    this.addChild(partialCurveMesh)
                } else {
                    val trianglesAndNormals = geometryCalculator.calculateTriangles(listShapes, cover = cover)
                    val partialCurveMesh = PartialCurveMesh(trianglesAndNormals.first, trianglesAndNormals.second)
                    this.addChild(partialCurveMesh)
                }
            }
        }
    }
}
