package graphics.scenery.geometry

import graphics.scenery.BufferUtils
import graphics.scenery.Mesh
import graphics.scenery.attribute.geometry.HasGeometry
import graphics.scenery.utils.extensions.toFloatArray
import org.joml.*

/**
 * Constructs a geometry along the calculates points of a Spline.
 * The number n corresponds to the number of segments you wish to have between your control points.
 * The spline and the baseShape lambda must both have the same number of elements, otherwise, the curve is no
 * longer well defined. Concerning the individual baseShapes, no lines must cross for the body of the curve to
 * be visualized flawlessly. Furthermore, all baseShapes ought to be convex.
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 * @param [spline] the spline along which the geometry will be rendered
 * @param [baseShape] a lambda which returns all the baseShapes along the curve
 * @param [firstPerpendicularVector] vector to which the first frenet tangent shall be perpendicular to.
 * @param [partitionAlongControlpoints] flag to indicate that the curve should be divided into subcurves, one for each
 * controlpoint, note that this option prohibits the use of different baseShapes
 * @param [partitionAlongSplinePoints] flag to indicate that the curve is to be divided at each splinepoint. Please note,
 * [partitionAlongControlpoints] and [partitionAlongSplinePoints] are mutually exclusive in this class
 */
class Curve(spline: Spline, private val partitionAlongControlpoints: Boolean = true, private val partitionAlongSplinePoints: Boolean = false,
            private val firstPerpendicularVector: Vector3f = Vector3f(0f, 0f, 0f),
            baseShape: () -> List<List<Vector3f>>): Mesh("CurveGeometry"), HasGeometry {
    val chain = spline.splinePoints()
    private val sectionVertices = spline.verticesCountPerSection()
    val countList = ArrayList<Int>(50).toMutableList()
    val frenetFrames = FrenetFramesCalc(spline, firstPerpendicularVector).computeFrenetFrames()
    val baseShapes = baseShape.invoke()

    /*
     * This function renders the spline.
     * [baseShape] It takes a lambda as a parameter, which is the shape of the
     * curve.
     * If you choose, for example, to have a square as a base shape, your spline will look like
     * a banister.
     */
    init {
        if (chain.isEmpty()) {
            logger.warn("The spline provided for the Curve is empty.")
        }
        if (partitionAlongControlpoints && partitionAlongSplinePoints) {
            logger.warn("Please partition along controlpoints xor partition further along the splinepoints.")
        }
        val bases = FrenetFramesCalc(spline, firstPerpendicularVector).calcBases(frenetFrames)
        val baseShapes = baseShape.invoke()
        val transformedBaseShapes = ArrayList<List<Vector3f>>(baseShapes.size)
        baseShapes.forEachIndexed { index, shape ->
            val transformedShape = ArrayList<Vector3f>(shape.size)
            shape.forEach { point ->
                val transformedPoint = Vector3f()
                bases[index].transformPosition(point, transformedPoint)
                transformedShape.add(transformedPoint)
            }
            transformedBaseShapes.add(transformedShape)
        }

        if(partitionAlongControlpoints || partitionAlongSplinePoints) {
            if(transformedBaseShapes.size < sectionVertices +1) {
                println(transformedBaseShapes.size)
            }
            val subShapes = if(partitionAlongControlpoints && !partitionAlongSplinePoints) { transformedBaseShapes.windowed(sectionVertices+1, sectionVertices+1, true) }
            else { transformedBaseShapes.windowed(2, 1, true) }

            subShapes.forEachIndexed { index, list ->
                //fill gaps
                val arrayList = list as ArrayList
                if(index != subShapes.size -1) {
                    arrayList.add(subShapes[index+1][0])
                }
                val i = when (index) {
                    0 -> {
                        0
                    }
                    subShapes.size - 1 -> {
                        2
                    }
                    else -> {
                        1
                    }
                }
                val partialCurve = PartialCurve(VerticesCalculation().calculateTriangles(arrayList, i))
                this.addChild(partialCurve)
            }
        }
        else {
            var partialCurveSize = 1
            baseShapes.windowed(2, 1) { frame ->
                when (frame[0].size) {
                    frame[1].size -> {
                        partialCurveSize++
                    }
                    else -> {
                        countList.add(partialCurveSize)
                        partialCurveSize = 1
                    }
                }
            }
            countList.add(partialCurveSize)
            var position = 0
            var lastShapeUnique = false
            if (countList.last() == 1) {
                countList.removeAt(countList.lastIndex)
                lastShapeUnique = true
            }

            countList.forEachIndexed { index, count ->
                val partialCurveGeometry = ArrayList<List<Vector3f>>(count)
                for (j in 0 until count) {
                    partialCurveGeometry.add(transformedBaseShapes[position])
                    position++
                }
                val helpPosition = position
                //fill the gaps between the different shapes
                if (helpPosition < bases.lastIndex) {
                    val shape = baseShapes[helpPosition - 1]
                    val shapeVertexList = ArrayList<Vector3f>(shape.size)
                    shape.forEach {
                        val vec = Vector3f()
                        shapeVertexList.add(bases[helpPosition].transformPosition(it, vec))
                    }
                    partialCurveGeometry.add(shapeVertexList)
                }
                //edge case: the last shape is different from its predecessor
                if (lastShapeUnique && helpPosition == bases.lastIndex) {
                    val shape = baseShapes[helpPosition - 1]
                    val shapeVertexList = ArrayList<Vector3f>(shape.size)
                    shape.forEach {
                        val vec = Vector3f()
                        shapeVertexList.add(bases[helpPosition].transformPosition(it, vec))
                    }
                    partialCurveGeometry.add(shapeVertexList)
                }
                val i = if (index == 0) {
                    0
                } else if (index == countList.size - 1) {
                    2
                } else {
                    1
                }
                val partialCurve = PartialCurve(VerticesCalculation().calculateTriangles(partialCurveGeometry, i))
                this.addChild(partialCurve)
            }
        }
    }

    class VerticesCalculation(private val partitionAlongSplinePoints: Boolean = false,) {
        var coverTopSize: Int = 0
        var coverBottomSize: Int = 0
        /**
         * This function calculates the triangles for the rendering. It takes as a parameter
         * the [curveGeometry] List which contains all the baseShapes transformed and translated
         * along the curve.
         */
        fun calculateTriangles(curveGeometry: List<List<Vector3f>>, addCoverOrTop: Int = 2): ArrayList<Vector3f> {
            val verticesVectors = ArrayList<Vector3f>(curveGeometry.flatten().size * 6 + curveGeometry[0].size + 1)
            if (curveGeometry.isEmpty()) {
                return verticesVectors
            }
            if (addCoverOrTop == 0) {
                verticesVectors.addAll(getCoverVertices(curveGeometry[0], true))
            }
            //if none of the lists in the curveGeometry differ in size, distinctBy leaves only one element
            if (curveGeometry.distinctBy { it.size }.size == 1) {
                curveGeometry.dropLast(1).forEachIndexed { shapeIndex, shape ->
                    shape.dropLast(1).forEachIndexed { vertexIndex, _ ->

                        verticesVectors.add(curveGeometry[shapeIndex][vertexIndex])
                        verticesVectors.add(curveGeometry[shapeIndex][vertexIndex + 1])
                        verticesVectors.add(curveGeometry[shapeIndex + 1][vertexIndex])

                        verticesVectors.add(curveGeometry[shapeIndex][vertexIndex + 1])
                        verticesVectors.add(curveGeometry[shapeIndex + 1][vertexIndex + 1])
                        verticesVectors.add(curveGeometry[shapeIndex + 1][vertexIndex])
                    }
                    verticesVectors.add(curveGeometry[shapeIndex][0])
                    verticesVectors.add(curveGeometry[shapeIndex + 1][0])
                    verticesVectors.add(curveGeometry[shapeIndex + 1][shape.lastIndex])

                    verticesVectors.add(curveGeometry[shapeIndex + 1][shape.lastIndex])
                    verticesVectors.add(curveGeometry[shapeIndex][shape.lastIndex])
                    verticesVectors.add(curveGeometry[shapeIndex][0])
                }
            } else {
                throw IllegalArgumentException("The baseShapes must not differ in size!")
            }
            if (addCoverOrTop == 2) {
                verticesVectors.addAll(getCoverVertices(curveGeometry.last(), false))
            }
            return verticesVectors
        }

        /**
        This function contains a generalized algorithm for the cover of a curve. It works like a pot and a lit. If you cover
        the bottom of a curve, the triangles should be arranged counterclockwise, for the top clockwise - this is signified
        by [ccw].
         */
        private fun getCoverVertices(list: List<Vector3f>, ccw: Boolean): ArrayList<Vector3f> {
            val size = list.size
            val verticesList = ArrayList<Vector3f>(size + (size / 2))
            val workList = ArrayList<Vector3f>(size)
            workList.addAll(list)
            if (size >= 3) {
                /* The algorithm must not stop before the last triangle. The next five lines ensure, therefore,
                   that the last triangle, which contains the last point as well as the first point, is included.
             */
                when (size % 1) {
                    0 -> {  workList.add(list[0])
                        workList.add(list[1]) }
                    1 -> { workList.add(list[0]) }
                }
                val newList = ArrayList<Vector3f>((size + (size / 2)) / 2)
                workList.windowed(3, 2) { triangle ->
                    if (ccw) {
                        verticesList.add(triangle[0])
                        verticesList.add(triangle[2])
                        verticesList.add(triangle[1])
                    } else {
                        for (i in 0..2) {
                            verticesList.add(triangle[i])
                        }
                    }
                    newList.add(triangle[0])
                }
                verticesList.addAll(getCoverVertices(newList, ccw))
            }
            // size of top/bottom cover is needed to divide the curve children further
            if (partitionAlongSplinePoints) {
                //top
                if(ccw) {
                    coverTopSize = verticesList.size
                }
                //bottom
                else {
                    coverBottomSize = verticesList.size
                }
            }
            return verticesList
        }
    }

    /**
     * Each child of the curve must be, per definition, another Mesh. Therefore, this class turns a List of
     * vertices into a Mesh.
     */
    class PartialCurve(verticesVectors: List<Vector3f>) : Mesh("PartialCurve") {
        init {
            geometry {
                vertices = BufferUtils.allocateFloat(verticesVectors.size * 3)
                verticesVectors.forEach {
                    vertices.put(it.toFloatArray())
                }
                vertices.flip()
                texcoords = BufferUtils.allocateFloat(verticesVectors.size * 2)
                recalculateNormals()

                boundingBox = generateBoundingBox()
            }
            boundingBox = generateBoundingBox()
        }
    }
}
