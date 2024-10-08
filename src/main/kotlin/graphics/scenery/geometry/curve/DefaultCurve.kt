package graphics.scenery.geometry.curve

import graphics.scenery.BufferUtils
import graphics.scenery.Mesh
import graphics.scenery.geometry.Spline
import graphics.scenery.geometry.curve.FrenetCurve.Companion.computeFrenetFrames
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.plusAssign
import org.joml.Vector2f
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
    final override val baseShapes: () -> SegmentedShapeList,
    final override val firstPerpendicularVector: Vector3f = Vector3f(0.0f),
    createSubShapes: Boolean = true
): FrenetCurve, Mesh("DefaultCurve") {

    final override val frenetFrames: () -> List<FrenetFrame> =
        { computeFrenetFrames(spline, firstPerpendicularVector) }
    init {
        if(createSubShapes) {
            createSubShapes()
        }
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
                    // if there is only a single shape, we need to add a cover for both sides,
                    // so we override the default cover variable here
                    val cv = if(subShapes.size == 2) {
                        CurveCover.Both
                    } else {
                        cover
                    }

                    val arrayListShapes = listShapes as ArrayList
                    arrayListShapes.add(transformedBaseShapes.last())
                    val (vertices, normals) = calculateTriangles(arrayListShapes,
                                                                 cover = cv)
                    val partialCurve = PartialCurve(vertices, normals)
                    this.addChild(partialCurve)
                } else {
                    val (vertices, normals) = calculateTriangles(listShapes, cover = cover)
                    val partialCurve = PartialCurve(vertices, normals)
                    this.addChild(partialCurve)
                }
            }
        }
    }

    private fun addVN(
        v1: graphics.scenery.geometry.curve.Vertex,
        v2: graphics.scenery.geometry.curve.Vertex,
        v3: graphics.scenery.geometry.curve.Vertex,
        vertices: FloatBuffer
    ): Vector3f {
        vertices += v1.v
        vertices += v3.v
        vertices += v2.v

        return ((v3.v - v1.v).cross(v2.v - v1.v)).normalize()
    }

    /**
     * This function calculates the triangles for the curve rendering. It takes as a parameter
     * the [curveGeometry] List which contains all the baseShapes transformed and translated
     * along the curve.
     */
    fun calculateTriangles(curveGeometry: SegmentedShapeList, cover: CurveCover = CurveCover.None): Pair<FloatBuffer, FloatBuffer> {
        val sizeWithoutCover = (curveGeometry.sumOf { it.vertices.size } * 6 - (curveGeometry.last().vertices.size* 3 + curveGeometry.first().vertices.size * 3)) * 3

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
        if (curveGeometry.distinctBy { it.vertices.size }.size == 1) {
            val intermediateNormals = ArrayList<ArrayList<Vector3f>>(curveGeometry.sumOf { it.vertices.size }/curveGeometry[0].vertices.size)
            // subList is faster than dropLast. Notice though that the toIndex argument is exclusive!
            curveGeometry.subList(0, curveGeometry.size - 1).forEachIndexed { shapeIndex, shape ->

                val intermediateNormalSection = ArrayList<Vector3f>(shape.vertices.size)
                val currentShape = curveGeometry[shapeIndex]
                val nextShape = curveGeometry[shapeIndex + 1]

                // subList is faster than dropLast. Notice though that the toIndex argument is exclusive!
                shape.vertices.subList(0, shape.vertices.size - 1).forEachIndexed { vertexIndex, _ ->
                    val n1 = addVN(
                        v1 = currentShape.vertices[vertexIndex],
                        v2 = currentShape.vertices[vertexIndex + 1],
                        v3 = nextShape.vertices[vertexIndex],
                        vertices = verticesWithoutCoverBuffer,
                    )

                    val n2 = addVN(
                        v1 = currentShape.vertices[vertexIndex + 1],
                        v2 = nextShape.vertices[vertexIndex + 1],
                        v3 = nextShape.vertices[vertexIndex],
                        vertices = verticesWithoutCoverBuffer,
                    )

                    val faceNormal = (n1 + n2).normalize()
                    intermediateNormalSection.add(faceNormal)
                    intermediateNormalSection.add(faceNormal)
                    intermediateNormalSection.add(faceNormal)
                    intermediateNormalSection.add(faceNormal)
                    intermediateNormalSection.add(faceNormal)
                    intermediateNormalSection.add(faceNormal)
                }

                val n1 = addVN(
                    v1 = currentShape.vertices[shape.vertices.lastIndex],
                    v2 = currentShape.vertices[0],
                    v3 = nextShape.vertices[shape.vertices.lastIndex],
                    vertices = verticesWithoutCoverBuffer,
                )

                val n2 = addVN(
                    v1 = currentShape.vertices[0],
                    v2 = nextShape.vertices[0],
                    v3 = nextShape.vertices[shape.vertices.lastIndex],
                    vertices = verticesWithoutCoverBuffer,
                )
                val faceNormal = (n1 + n2).normalize()
                intermediateNormalSection.add(faceNormal)
                intermediateNormalSection.add(faceNormal)
                intermediateNormalSection.add(faceNormal)
                intermediateNormalSection.add(faceNormal)
                intermediateNormalSection.add(faceNormal)
                intermediateNormalSection.add(faceNormal)

                //add all triangle normals from this section
                intermediateNormals += intermediateNormalSection
            }

            //add the vertices and normals to the first cover to the final buffers
            if(cover == CurveCover.Top || cover == CurveCover.Both) {
                val newVerticesAndNormals = getCoverVertices(
                    curveGeometry.first().vertices, true,
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
            intermediateNormals.flatten().forEach { normalsBuffer += it }

            if (cover == CurveCover.Bottom || cover == CurveCover.Both) {
                val newVerticesAndNormals = getCoverVertices(
                    curveGeometry.last().vertices, false,
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
    private fun getCoverVertices(
        verticesList: List<graphics.scenery.geometry.curve.Vertex>,
        ccw: Boolean,
        perpendicularNormals: List<Vector3f>
    ): Pair<ArrayList<Vector3f>, ArrayList<Vector3f>> {
        //the direction of the cover triangle normals is the same for all triangles
        val surfaceNormal = if(!ccw) {
            ((Vector3f(verticesList.last().v).sub(Vector3f(verticesList.first().v)))
            .cross(Vector3f(verticesList[verticesList.size/2].v).sub(Vector3f(verticesList.first().v)))).normalize()
        }
        else {
            ((Vector3f(verticesList[verticesList.size/2].v).sub(Vector3f(verticesList.first().v)))
            .cross(Vector3f(verticesList.last().v).sub(Vector3f(verticesList.first().v)))).normalize()
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
    private fun getCoverVerticesRecursive(vertices: List<graphics.scenery.geometry.curve.Vertex>,
                                          normals: List<Vector3f>, ccw: Boolean): Pair<ArrayList<Vector3f>, ArrayList<Vector3f>> {
        val size = vertices.size
        //the complete list at the end of the calculation
        val verticesList = ArrayList<Vector3f>(size + (size / 2))
        val normalVectors = ArrayList<Vector3f>(verticesList.size)

        val order = if(!ccw) {
            listOf(0, 2, 1)
        } else {
            listOf(0, 1, 2)
        }

        if (vertices.size >= 3) {
            //working list for the respective iteration
            val workList = ArrayList<Pair<Vector3f, Vector3f>>(vertices.size)
            vertices.forEachIndexed { index, vertex -> workList.add(Pair(vertex.v, normals[index]))}
            //list containing vertices for the next iteration
            val newVertices = ArrayList<Vector3f>(vertices.size%3)
            //list containing normals for the next iteration
            val newNormals = ArrayList<Vector3f>(newVertices.size)
            //Ensures that the algorithm does not stop before the last triangle.
            if (size % 2 == 0) {
                workList.add(Pair(vertices.first().v, normals.first()))
            }

            workList.windowed(3, 2) { triangle ->
                verticesList.add(triangle[order[0]].first)
                verticesList.add(triangle[order[1]].first)
                verticesList.add(triangle[order[2]].first)

                // calculate face normal
                val n = (triangle[order[0]].second
                        + triangle[order[1]].second
                        + triangle[order[2]].second)
                    .normalize()
                normalVectors.add(n)
                normalVectors.add(n)
                normalVectors.add(n)

                newVertices.add(triangle[0].first)
                newNormals.add(triangle[0].second)
            }

            //check if the recursion has come to an end
            if (newVertices.size >= 3) {
                //to avoid gaps when the vertex number is odd
                if (size % 2 == 1) {
                    newVertices.add(vertices.last().v)
                }

                val nv = newVertices.map { Vertex(it, Vector3f(0.0f), Vector2f(0.0f)) }
                val newVerticesAndNormals = getCoverVerticesRecursive(nv, newNormals, ccw)
                verticesList.addAll(newVerticesAndNormals.first)
                normalVectors.addAll(newVerticesAndNormals.second)
            }
        }
        return Pair(verticesList, normalVectors)
    }

    /**
     * Computes the number of vertices for the triangles which cover up the curve's respective ends.
     */
    private fun Shape.computeCoverVerticesCount(cover: CurveCover): Int {
        if(cover == CurveCover.None)  {
            return  0
        }
        var coverVerticesCount = 0
        var subListSize = vertices.size
        while(subListSize > 2) {
            val odd = subListSize%2 == 1
            subListSize /= 2
            coverVerticesCount += subListSize
            if (odd) { subListSize ++ }
        }
        return coverVerticesCount*3
    }
}
