package graphics.scenery.geometry.curve

import graphics.scenery.geometry.Spline
import graphics.scenery.Mesh
import graphics.scenery.proteins.MathLine
import org.joml.*

/**
 * This class represents a Helix in 3D space. Currently, it needs a Spline which winds around an axis defined as a line
 * in space. Each spline point is assigned a baseShape. Finally, all the shapes get connected with triangles.
 * [axis] line around which the spline should wind
 * [spline] spline
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class Helix (private val axis: MathLine, override val spline: Spline,
             override val baseShapes: () -> List<List<Vector3f>>): Curve, Mesh("Helix") {
    val splinePoints = spline.splinePoints()
    private val shapes = baseShapes.invoke()
    private val axisVector = axis.direction
    private val axisPoint = axis.position
    private val geometryCalculator = CurveGeometryCalculation


    init {
        val pointsPerSection = spline.pointsPerSection()
        val transformedShapes = calculateTransformedShapes()
        val subShapes = transformedShapes.windowed(pointsPerSection + 1, pointsPerSection + 1, true)
        subShapes.forEachIndexed { index, list ->
            //fill gaps
            val arrayList = list as ArrayList
            if(index != subShapes.size -1) {
                arrayList.add(subShapes[index+1][0])
            }
            val cover = when (index) {
                0 -> {
                    CurveCover.Top
                }
                subShapes.size - 1 -> {
                    CurveCover.Bottom
                }
                else -> {
                    CurveCover.None
                }
            }
            this.addChild(calcMesh(arrayList, cover))
        }
    }

    /**
     * Transformation of the baseShapes along the spline, aligned with the helix axis.
     */
    private fun calculateTransformedShapes(): ArrayList<List<Vector3f>> {
        if(axisVector == Vector3f(0f, 0f, 0f)) {
            throw Exception("The direction vector of the axis must no become the null vector.")
        }
        val transformedShapes = ArrayList<List<Vector3f>>(splinePoints.size)
        splinePoints.forEachIndexed { index, point ->
            /*
            The coordinate systems which walk along the spline are calculated like so:
            The x axis is the axis direction.
            The y axis is the normalized vector from the spline point its neighbor on the axis with least distance
            between them. The y axis vector is then perpendicular to the axis vector, therefore, perpendicular to
            the x axis.*
            The z axis is the normalized cross product between the x and the y axis.

            *Calculate y
            - axis line: l = a + tb (a is the positional vector and b the direction)
            - spline point as: p
            The point on the line with the least distance to the spline point is:
            p' = a + t'b
            with t' = (p-a)*b / |b|^2  (with * being the dot product)
            Then y = (p-p') / |p-p'|
             */
            val iVec = Vector3f()
            val t = (point.sub(axisPoint, iVec)).dot(axisVector)/(axisVector.length()*axisVector.length())
            val intermediateAxis = Vector3f()
            intermediateAxis.set(axisVector)
            val plumbLine = Vector3f()
            axisPoint.add(intermediateAxis.mul(t), plumbLine)
            val xAxisI = Vector3f()
            xAxisI.set(axisVector).normalize()
            val yAxisI = Vector3f()
            point.sub(plumbLine, yAxisI).normalize()
            val zAxisI = Vector3f()
            xAxisI.cross(yAxisI, zAxisI).normalize()
            //point transformation
            val inversionMatrix = Matrix3f(xAxisI, yAxisI, zAxisI).invert()
            val xAxis = Vector3f()
            inversionMatrix.getColumn(0, xAxis).normalize()
            val yAxis = Vector3f()
            inversionMatrix.getColumn(1, yAxis).normalize()
            val zAxis = Vector3f()
            inversionMatrix.getColumn(2, zAxis).normalize()
            val transformMatrix = Matrix4f(xAxis.x(), yAxis.x(), zAxis.x(), 0f,
                xAxis.y(), yAxis.y(), zAxis.y(), 0f,
                xAxis.z(), yAxis.z(), zAxis.z(), 0f,
                point.x(), point.y(), point.z(), 1f)
            //if there is only a single base shape, just transform it
            when (shapes.size) {
                1 -> {
                    val shape = shapes.first()
                    transformedShapes.add(shape.map { shapePoint ->
                        val transformedPoint = Vector3f()
                        transformMatrix.transformPosition(shapePoint, transformedPoint)
                    })
                }
                splinePoints.size -> {
                    val shape = shapes[index]
                    transformedShapes.add(shape.map { shapePoint ->
                        val transformedPoint = Vector3f()
                        transformMatrix.transformPosition(shapePoint, transformedPoint)
                    })
                }
                //either there is one shape or exactly as many as there are spline points. There is no default for an
                // incorrect number of shapes
                else -> {
                    throw Exception("Not enough (or too many) shapes provided!")
                }
            }
        }
        return transformedShapes
    }

    private fun calcMesh(section: List<List<Vector3f>>, cover: CurveCover): Mesh {
        //algorithms from the curve calculation
        val helixSectionVertices = geometryCalculator.calculateTriangles(section, cover)
        return PartialCurve(helixSectionVertices.first, helixSectionVertices.second)
    }
}
