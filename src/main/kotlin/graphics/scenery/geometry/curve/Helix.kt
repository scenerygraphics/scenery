package graphics.scenery.geometry.curve

import graphics.scenery.geometry.Spline
import graphics.scenery.Mesh
import graphics.scenery.proteins.PositionDirection
import graphics.scenery.utils.extensions.X
import org.joml.*
import kotlin.math.absoluteValue

/**
 * This class represents a Helix in 3D space. Currently, it needs a Spline which winds around an axis defined as a line
 * in space. Each spline point is assigned a baseShape. Finally, all the shapes get connected with triangles.
 * [axis] line around which the spline should wind
 * [spline] spline
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class Helix (private val axis: PositionDirection,
             spline: Spline,
             baseShapes: () -> SegmentedShapeList): DefaultCurve(spline, baseShapes, createSubShapes = false) {

    init {
        val pointsPerSection = spline.pointsPerSection()
        val transformedShapes = calculateTransformedShapes()
        val subShapes = transformedShapes.windowed(pointsPerSection + 1, pointsPerSection, partialWindows = true)

        subShapes.forEachIndexed { index, listShapes ->
            //if the last section contains only a single shape, it will be added below
            if (listShapes.size > 1) {
                val cover = when (index) {
                    0 -> CurveCover.Top
                    subShapes.lastIndex -> CurveCover.Bottom
                    else -> CurveCover.None
                }

                //default behaviour: if the last section is a single shape,
                // it will be added to the last window of the shapes
                if (index == subShapes.lastIndex - 1 && subShapes.last().size == 1) {
                    val arrayListShapes = listShapes as ArrayList
                    arrayListShapes.add(transformedShapes.last())
                    this.addChild(generateMesh(arrayListShapes, CurveCover.Bottom))
                } else {
                    this.addChild(generateMesh(listShapes, cover))
                }
            }
        }
    }

    /**
     * Transformation of the baseShapes along the spline, aligned with the helix axis.
     */
    private fun calculateTransformedShapes(): ArrayList<Shape> {
        val shapes = baseShapes.invoke()
        val splinePoints = spline.splinePoints()

        if(axis.direction.length().absoluteValue <= 0.000001f) {
            throw IllegalArgumentException("The direction vector of the axis must not be the null vector.")
        }
        val transformedShapes = ArrayList<Shape>(splinePoints.size)
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
            val t = (point.sub(axis.position, iVec)).dot(axis.direction)/(axis.direction.length()*axis.direction.length())
            val intermediateAxis = Vector3f()
            intermediateAxis.set(axis.direction)
            val plumbLine = Vector3f()
            axis.position.add(intermediateAxis.mul(t), plumbLine)
            val xAxisI = Vector3f()
            xAxisI.set(axis.direction).normalize()
            val yAxisI = Vector3f()
            point.sub(plumbLine, yAxisI).normalize()
//            xAxisI.cross(yAxisI, zAxisI).normalize()
            val zAxisI = (xAxisI X yAxisI).normalize()
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
                    transformedShapes.add(Shape(shape.vertices.map { shapePoint ->
                        val transformedPoint = Vector3f()
                        transformMatrix.transformPosition(shapePoint.v, transformedPoint)

                        val transformedNormal = Vector3f()
                        transformMatrix.transformPosition(shapePoint.n, transformedNormal)

                        Vertex(transformedPoint, transformedNormal, shapePoint.uv)
                    }))
                }
                splinePoints.size -> {
                    val shape = shapes[index]
                    transformedShapes.add(Shape(shape.vertices.map { shapePoint ->
                        val transformedPoint = Vector3f()
                        transformMatrix.transformPosition(shapePoint.v, transformedPoint)

                        val transformedNormal = Vector3f()
                        transformMatrix.transformPosition(shapePoint.n, transformedNormal)

                        Vertex(transformedPoint, shapePoint.n, shapePoint.uv)
                    }))
                }
                //either there is one shape or exactly as many as there are spline points. There is no default for an
                // incorrect number of shapes
                else -> {
                    throw IllegalStateException("Not enough (or too many) shapes provided!")
                }
            }
        }
        return transformedShapes
    }

    private fun generateMesh(section: SegmentedShapeList, cover: CurveCover): Mesh {
        //algorithms from the curve calculation
        val helixSectionVertices = calculateTriangles(section, cover)
        return PartialCurve(helixSectionVertices.first, helixSectionVertices.second)
    }
}
