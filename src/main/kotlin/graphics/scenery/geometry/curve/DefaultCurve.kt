package graphics.scenery.geometry.curve

import graphics.scenery.BufferUtils
import graphics.scenery.Mesh
import graphics.scenery.geometry.Spline
import graphics.scenery.geometry.curve.FrenetCurve.Companion.computeFrenetFrames
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plusAssign
import org.joml.Vector3f
import java.nio.FloatBuffer

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
open class DefaultCurve(
    final override val spline: Spline,
    final override val baseShapes: () -> SegmentedBaseShapeList,
    final override val firstPerpendicularVector: Vector3f = Vector3f(0.0f),
): FrenetCurve, Mesh("DefaultCurve") {

    final override val frenetFrames: () -> List<FrenetFrame> =
        { computeFrenetFrames(spline, firstPerpendicularVector) }
    init {
        createSubShapes()
    }

    protected open fun createSubShapes() {
        val pointsPerSection = spline.pointsPerSection()
        val transformedBaseShapes = transformedBaseShapes(baseShapes.invoke(), frenetFrames.invoke())
        val subShapes = transformedBaseShapes.windowed(pointsPerSection+1, pointsPerSection, partialWindows = true)

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
                    arrayListShapes.add(transformedBaseShapes.last())
                    val trianglesAndNormals = calculateTriangles(arrayListShapes,
                                                                 cover = CurveCover.Bottom)
                    val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
                    this.addChild(partialCurve)
                } else {
                    val trianglesAndNormals = calculateTriangles(listShapes, cover = cover)
                    val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
                    this.addChild(partialCurve)
                }
            }
        }
    }

    /**
     * This function calculates the triangles for the curve rendering. It takes as a parameter
     * the [curveGeometry] List which contains all the baseShapes transformed and translated
     * along the curve.
     */
    fun calculateTriangles(curveGeometry: SegmentedBaseShapeList, cover: CurveCover = CurveCover.None): Pair<FloatBuffer, FloatBuffer> {
        val sizeWithoutCover = (curveGeometry.sumOf{ it.size } * 6 - (curveGeometry.last().size* 3 + curveGeometry.first().size*3)) * 3

        val sizeWithCover = if(cover == CurveCover.Both) {
            sizeWithoutCover + curveGeometry.first().computeCoverVerticesCount(cover)*3 +
                curveGeometry.last().computeCoverVerticesCount(cover)*3
        } else {
            sizeWithoutCover + curveGeometry.first().computeCoverVerticesCount(cover)*3
        }

        val verticesWithoutCoverBuffer = BufferUtils.allocateFloat(sizeWithoutCover)
        val verticesBuffer = BufferUtils.allocateFloat(sizeWithCover)
        val normalsBuffer = BufferUtils.allocateFloat(sizeWithCover)
        if (curveGeometry.isEmpty()) {
            return Pair(verticesBuffer, normalsBuffer)
        }
        //if none of the lists in the curveGeometry differ in size, distinctBy leaves only one element
        if (curveGeometry.distinctBy { it.size }.size == 1) {
            val intermediateNormals = ArrayList<ArrayList<Vector3f>>(curveGeometry.sumOf { it.size }/curveGeometry[0].size)
            curveGeometry.dropLast(1).forEachIndexed { shapeIndex, shape ->

                val intermediateNormalSection = ArrayList<Vector3f>(shape.size)

                shape.dropLast(1).forEachIndexed { vertexIndex, _ ->
                    val triangle1Point1 = curveGeometry[shapeIndex][vertexIndex]
                    val triangle1Point2 = curveGeometry[shapeIndex][vertexIndex + 1]
                    val triangle1Point3 = curveGeometry[shapeIndex + 1][vertexIndex]

                    verticesWithoutCoverBuffer += triangle1Point1
                    verticesWithoutCoverBuffer += triangle1Point2
                    verticesWithoutCoverBuffer += triangle1Point3

                    //normal calculation triangle 1
                    val normal1 = (triangle1Point3 - triangle1Point1)
                        .cross(triangle1Point2 - triangle1Point1)
                    intermediateNormalSection.add(normal1.normalize())

                    val triangle2Point1 = curveGeometry[shapeIndex][vertexIndex + 1]
                    val triangle2Point2 = curveGeometry[shapeIndex + 1][vertexIndex + 1]
                    val triangle2Point3 = curveGeometry[shapeIndex + 1][vertexIndex]

                    verticesWithoutCoverBuffer += triangle2Point1
                    verticesWithoutCoverBuffer += triangle2Point2
                    verticesWithoutCoverBuffer += triangle2Point3

                    //normal calculation triangle 2
                    val normal2 = (triangle2Point3 - triangle2Point1)
                        .cross(triangle2Point2 - triangle2Point1)
                    intermediateNormalSection.add(normal2.normalize())
                }

                val triangle1Point1 = curveGeometry[shapeIndex][shape.lastIndex]
                val triangle1Point2 = curveGeometry[shapeIndex][0]
                val triangle1Point3 = curveGeometry[shapeIndex + 1][shape.lastIndex]

                verticesWithoutCoverBuffer += triangle1Point1
                verticesWithoutCoverBuffer += triangle1Point2
                verticesWithoutCoverBuffer += triangle1Point3

                //normal calculation triangle 1
                val normal1 = (triangle1Point3 - triangle1Point1)
                    .cross(triangle1Point2 - triangle1Point1)
                intermediateNormalSection.add(normal1.normalize())

                val triangle2Point1 = curveGeometry[shapeIndex][0]
                val triangle2Point2 = curveGeometry[shapeIndex + 1][0]
                val triangle2Point3 = curveGeometry[shapeIndex + 1][shape.lastIndex]

                verticesWithoutCoverBuffer += triangle2Point1
                verticesWithoutCoverBuffer += triangle2Point2
                verticesWithoutCoverBuffer += triangle2Point3

                //normal calculation triangle 2
                val normal2 = (triangle2Point3 - triangle2Point1)
                    .cross(triangle2Point2 - triangle2Point1)
                intermediateNormalSection.add(normal2.normalize())

                //add all triangle normals from this section
                intermediateNormals.add(intermediateNormalSection)
            }

            //add the vertices and normals to the first cover to the final buffers
            if(cover == CurveCover.Top || cover == CurveCover.Both) {
                val newVerticesAndNormals = getCoverVertices(
                    curveGeometry.first(), true,
                    intermediateNormals
                        .first()
                        .filterIndexed { index, _ -> index % 2 == 1 }
                        .map { it.normalize() }
                )
                newVerticesAndNormals.first.forEach {
                    verticesBuffer += it
                }
                newVerticesAndNormals.second.forEach {
                    normalsBuffer += it
                }
            }

            //add the vertices and normals of the curve's body to the buffers
            verticesWithoutCoverBuffer.flip()
            verticesBuffer.put(verticesWithoutCoverBuffer)
            val curveNormals = computeNormals(intermediateNormals, curveGeometry.first().size)
            curveNormals.flip()
            normalsBuffer.put(curveNormals)

            if (cover == CurveCover.Bottom || cover == CurveCover.Both) {
                val newVerticesAndNormals = getCoverVertices(
                    curveGeometry.last(), false,
                    intermediateNormals
                        .last()
                        .filterIndexed { index, _ -> index % 2 == 0 }
                        .map { it.normalize() }
                )
                newVerticesAndNormals.first.forEach {
                    verticesBuffer += it
                }
                newVerticesAndNormals.second.forEach {
                    normalsBuffer += it
                }
            }
        } else {
            throw IllegalArgumentException("The baseShapes must not differ in size!")
        }
        return Pair(verticesBuffer, normalsBuffer)
    }

    /**
    This function contains a generalized algorithm for the cover of a curve. It works like a pot and a lit. If you cover
    the bottom of a curve, the triangles should be arranged counterclockwise, for the top clockwise - this is signified
    by [ccw].
     */
    private fun getCoverVertices(verticesList: List<Vector3f>, ccw: Boolean, perpendicularNormals: List<Vector3f>): Pair<ArrayList<Vector3f>, ArrayList<Vector3f>> {
        //the direction of the cover triangle normals is the same for all triangles
        val surfaceNormal = if(!ccw) {
            ((Vector3f(verticesList.last()).sub(Vector3f(verticesList.first())))
            .cross(Vector3f(verticesList[verticesList.size/2]).sub(Vector3f(verticesList.first())))).normalize()
        }
        else {
            ((Vector3f(verticesList[verticesList.size/2]).sub(Vector3f(verticesList.first())))
            .cross(Vector3f(verticesList.last()).sub(Vector3f(verticesList.first())))).normalize()
        }
        //compute the normal for each of the vertices at the beginning/end of the curve
        val vertexNormals = perpendicularNormals.mapIndexed { index, normal ->
            if(index == 0)  { Vector3f(surfaceNormal).add(normal).add(perpendicularNormals.last()).normalize() }
            else { Vector3f(surfaceNormal).add(normal).add(perpendicularNormals[index-1]).normalize() }
        }

        return getCoverVerticesRecursive(verticesList, vertexNormals, ccw)
    }

    /**
     * Recursive helper function saving the triangle vertices along with their normals for the Curve cover.
     * The function takes packages of three consecutive vertices from the curves beginning/end and stores them as
     * a triangle. The first triangle point is stored for the next iteration. The recursion runs until no more
     * points are left.
     */
    private fun getCoverVerticesRecursive(vertices: List<Vector3f>, normals: List<Vector3f>, ccw: Boolean): Pair<ArrayList<Vector3f>, ArrayList<Vector3f>> {
        val size = vertices.size
        //the complete list at the end of the calculation
        val verticesList = ArrayList<Vector3f>(size + (size / 2))
        val normalVectors = ArrayList<Vector3f>(verticesList.size)

        if (vertices.size >= 3) {
            //working list for the respective iteration
            val workList = ArrayList<Pair<Vector3f, Vector3f>>(vertices.size)
            vertices.forEachIndexed { index, vertex -> workList.add(Pair(vertex, normals[index]))}
            //list containing vertices for the next iteration
            val newVertices = ArrayList<Vector3f>(vertices.size%3)
            //list containing normals for the next iteration
            val newNormals = ArrayList<Vector3f>(newVertices.size)
            //Ensures that the algorithm does not stop before the last triangle.
            if (size % 2 == 0) {
                workList.add(Pair(vertices.first(), normals.first()))
            }

            workList.windowed(3, 2) { triangle ->
                if (ccw) {
                    verticesList.add(triangle[0].first)
                    verticesList.add(triangle[2].first)
                    verticesList.add(triangle[1].first)
                    normalVectors.add(triangle[0].second)
                    normalVectors.add(triangle[2].second)
                    normalVectors.add(triangle[1].second)
                } else {
                    for (i in 0..2) {
                        verticesList.add(triangle[i].first)
                        normalVectors.add(triangle[i].second)
                    }
                    //add normals
                }
                newVertices.add(triangle[0].first)
                newNormals.add(triangle[0].second)
            }

            //check if the recursion has come to an end
            if (newVertices.size >= 3) {
                //to avoid gaps when the vertex number is odd
                if (size % 2 == 1) {
                    newVertices.add(vertices.last())
                }

                val newVerticesAndNormals = getCoverVerticesRecursive(newVertices, newNormals, ccw)
                verticesList.addAll(newVerticesAndNormals.first)
                normalVectors.addAll(newVerticesAndNormals.second)
            }
        }
        return Pair(verticesList, normalVectors)
    }

    /**
     * Computes the normals for each vertex then gives this list to the function [orderNormals] to give each of
     * triangle vertices it's normal.
     */
    private fun computeNormals(intermediateNormals: ArrayList<ArrayList<Vector3f>>, shapeSize: Int): FloatBuffer {
        val normalsOfVertices = ArrayList<ArrayList<Vector3f>>(intermediateNormals.size*shapeSize + shapeSize)
        //calculate normals for every vertex
        val firstSectionNormals = ArrayList<Vector3f>(shapeSize)
        for(shapeIndex in 0 until shapeSize) {
            val sectionIndex = shapeIndex*2
            val vertexNormal = Vector3f()
            when(shapeIndex) {
                0 -> {
                    vertexNormal.add(intermediateNormals.first()[sectionIndex])
                    vertexNormal.add(intermediateNormals.first()[sectionIndex+1])
                    vertexNormal.add(intermediateNormals.first().last())
                }
                shapeSize - 1 -> {
                    vertexNormal.add(intermediateNormals.first()[sectionIndex])
                    vertexNormal.add(intermediateNormals.first().first())
                    vertexNormal.add(intermediateNormals.first()[sectionIndex-1])
                }
                else -> {
                    vertexNormal.add(intermediateNormals.first()[sectionIndex-1])
                    vertexNormal.add(intermediateNormals.first()[sectionIndex+1])
                    vertexNormal.add(intermediateNormals.first()[sectionIndex])
                }
            }
            firstSectionNormals.add(vertexNormal.normalize())
        }
        normalsOfVertices.add(firstSectionNormals)
        intermediateNormals.windowed(size = 2, 1) { section ->
            val allSectionNormals = ArrayList<Vector3f>(shapeSize)
            for(shapeIndex in 0 until shapeSize) {
                val sectionIndex = shapeIndex*2
                val vertexNormal = Vector3f()
                when(shapeIndex) {
                    0 -> {
                        vertexNormal.add(section[1].first())
                        vertexNormal.add(section[1].last())
                        vertexNormal.add(section[1].drop(1).last())
                        vertexNormal.add(section[0].last())
                        vertexNormal.add(section[0].first())
                        vertexNormal.add(section[0][sectionIndex+1])
                    }
                    else -> {
                        vertexNormal.add(section[1][sectionIndex])
                        vertexNormal.add(section[1][sectionIndex-1])
                        vertexNormal.add(section[1][sectionIndex-2])
                        vertexNormal.add(section[0][sectionIndex-1])
                        vertexNormal.add(section[0][sectionIndex])
                        vertexNormal.add(section[0][sectionIndex+1])
                    }
                }
                allSectionNormals.add(vertexNormal.normalize())
            }
            normalsOfVertices.add(allSectionNormals)
        }

        val lastSectionNormals = ArrayList<Vector3f>(shapeSize)
        for(shapeIndex in 0 until shapeSize) {
            val sectionIndex = shapeIndex*2
            val vertexNormal = Vector3f()
            when(shapeIndex) {
                0 -> {
                    vertexNormal.add(intermediateNormals.last()[sectionIndex])
                    vertexNormal.add(intermediateNormals.last()[sectionIndex+1])
                    vertexNormal.add(intermediateNormals.last().last())
                }
                shapeSize-1 -> {
                    vertexNormal.add(intermediateNormals.last()[sectionIndex])
                    vertexNormal.add(intermediateNormals.last().first())
                    vertexNormal.add(intermediateNormals.last()[sectionIndex-1])
                }
                else -> {
                    vertexNormal.add(intermediateNormals.last()[sectionIndex])
                    vertexNormal.add(intermediateNormals.last()[sectionIndex+1])
                    vertexNormal.add(intermediateNormals.last()[sectionIndex-1])
                }
            }
            lastSectionNormals.add(vertexNormal.normalize())
        }
        normalsOfVertices.add(lastSectionNormals)
        return orderNormals(normalsOfVertices)
    }

    /**
     * Orders the normals in the same structure as the triangle vertices.
     */
    private fun orderNormals(verticesNormals: ArrayList<ArrayList<Vector3f>>): FloatBuffer {
        val finalNormalsSize = (verticesNormals.sumOf{ it.size }*6 - (verticesNormals.last().size* 3 + verticesNormals.first().size*3))*3
        val finalNormalsBuffer = BufferUtils.allocateFloat(finalNormalsSize)
        finalNormalsBuffer.position(finalNormalsSize)

        return finalNormalsBuffer
        // TODO: Bring back actual normal calculation
        /*
        verticesNormals.dropLast(1).forEachIndexed { shapeIndex, shape ->
            shape.dropLast(1).forEachIndexed { vertexIndex, _ ->

                finalNormalsBuffer += verticesNormals[shapeIndex][vertexIndex]
                finalNormalsBuffer += verticesNormals[shapeIndex][vertexIndex + 1]
                finalNormalsBuffer += verticesNormals[shapeIndex +1][vertexIndex]

                finalNormalsBuffer += verticesNormals[shapeIndex][vertexIndex + 1]
                finalNormalsBuffer += verticesNormals[shapeIndex + 1][vertexIndex + 1]
                finalNormalsBuffer += verticesNormals[shapeIndex + 1][vertexIndex]
            }

            finalNormalsBuffer += verticesNormals[shapeIndex][shape.lastIndex]
            finalNormalsBuffer += verticesNormals[shapeIndex][0]
            finalNormalsBuffer += verticesNormals[shapeIndex + 1][shape.lastIndex]

            finalNormalsBuffer += verticesNormals[shapeIndex][0]
            finalNormalsBuffer += verticesNormals[shapeIndex + 1][0]
            finalNormalsBuffer += verticesNormals[shapeIndex + 1][shape.lastIndex]
        }
        return finalNormalsBuffer
        */
    }

    /**
     * Computes the number of vertices for the triangles which cover up the curve's respective ends.
     */
    private fun <T> List<T>.computeCoverVerticesCount(cover: CurveCover): Int {
        if(cover == CurveCover.None)  {return  0 }
        var coverVerticesCount = 0
        var subListSize = this.size
        while(subListSize > 2) {
            val odd = subListSize%2 == 1
            subListSize /= 2
            coverVerticesCount += subListSize
            if (odd) { subListSize ++ }
        }
        return coverVerticesCount*3
    }
}
