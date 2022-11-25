package graphics.scenery.geometry

import graphics.scenery.BufferUtils
import graphics.scenery.Mesh
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.primitives.Arrow
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.toFloatArray
import org.joml.*
import kotlin.Float.Companion.MIN_VALUE
import kotlin.math.acos

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
 */

class Curve(spline: Spline, partitionAlongControlpoints: Boolean = true, private val firstPerpendicularVector: Vector3f = Vector3f(0f, 0f, 0f),
            baseShape: () -> List<List<Vector3f>>): Mesh("CurveGeometry") {
    val chain = spline.splinePoints()
    private val sectionVertices = spline.verticesCountPerSection()
    private val countList = ArrayList<Int>(50).toMutableList()

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
        val bases = computeFrenetFrames(chain as ArrayList<Vector3f>).map { (t, n, b, tr) ->
            val inverseMatrix = Matrix3f(b.x(), n.x(), t.x(),
                    b.y(), n.y(), t.y(),
                    b.z(), n.z(), t.z()).invert()
            val nb = Vector3f()
            inverseMatrix.getColumn(0, nb).normalize()
            val nn = Vector3f()
            inverseMatrix.getColumn(1, nn).normalize()
            val nt = Vector3f()
            inverseMatrix.getColumn(2, nt).normalize()
            Matrix4f(
                    nb.x(), nn.x(), nt.x(), 0f,
                    nb.y(), nn.y(), nt.y(), 0f,
                    nb.z(), nn.z(), nt.z(), 0f,
                    tr.x(), tr.y(), tr.z(), 1f)
        }
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

        if(partitionAlongControlpoints) {
            if(transformedBaseShapes.size < sectionVertices +1) {
                println(transformedBaseShapes.size)
            }
            val subShapes = transformedBaseShapes.windowed(sectionVertices+1, sectionVertices+1, true)
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
                    subShapes.lastIndex -> {
                        2
                    }
                    else -> {
                        1
                    }
                }
                val trianglesAndNormals = calculateTriangles(arrayList, addCoverOrTop = i)
                val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
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
                val trianglesAndNormals = calculateTriangles(partialCurveGeometry, i)
                val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
                this.addChild(partialCurve)
            }
        }
    }

    /**
     * This function calculates the tangent at a given index.
     * [i] index of the curve (not the geometry!)
     */
    private fun getTangent(i: Int): Vector3f {
        if(chain.size >= 3) {
            val tangent = Vector3f()
            when (i) {
                0 -> { ((chain[1].sub(chain[0], tangent)).normalize()) }
                1 -> { ((chain[2].sub(chain[0], tangent)).normalize()) }
                chain.lastIndex - 1 -> { ((chain[i + 1].sub(chain[i - 1], tangent)).normalize()) }
                chain.lastIndex -> { ((chain[i].sub(chain[i - 1], tangent)).normalize()) }
                else -> {
                    chain[i+1].sub(chain[i-1], tangent).normalize()
                }
            }
            return tangent
        }
        else {
            throw Exception("The spline deosn't provide enough points")
        }
    }

    /**
     * Data class to store Frenet frames (wandering coordinate systems), consisting of [tangent], [normal], [binormal]
     */
    data class FrenetFrame(val tangent: Vector3f, var normal: Vector3f, var binormal: Vector3f, val translation: Vector3f)
    /**
     * This function returns the frenet frames along the curve. This is essentially a new
     * coordinate system which represents the form of the curve. For details concerning the
     * calculation see: http://www.cs.indiana.edu/pub/techreports/TR425.pdf
     */
    fun computeFrenetFrames(curve: ArrayList<Vector3f>): List<FrenetFrame> {

        val frenetFrameList = ArrayList<FrenetFrame>(curve.size)

        if(curve.isEmpty()) {
            return frenetFrameList
        }

        //adds all the tangent vectors
        curve.forEachIndexed { index, _ ->
            val frenetFrame = FrenetFrame(getTangent(index), Vector3f(), Vector3f(), curve[index])
            frenetFrameList.add(frenetFrame)
        }
        var min = MIN_VALUE
        val vec = Vector3f(0f, 0f, 0f)
        vec.set(firstPerpendicularVector)
        if(vec == Vector3f(0f, 0f, 0f)) {
            val normal = Vector3f()
            if (frenetFrameList[0].tangent.x() <= min) {
                min = frenetFrameList[0].tangent.x()
                normal.set(1f, 0f, 0f)
            }
            if (frenetFrameList[0].tangent.y() <= min) {
                min = frenetFrameList[0].tangent.y()
                normal.set(0f, 1f, 0f)
            }
            if (frenetFrameList[0].tangent.z() <= min) {
                normal.set(0f, 0f, 1f)
            } else {
                normal.set(1f, 0f, 0f).normalize()
            }
            frenetFrameList[0].tangent.cross(normal, vec).normalize()
        }
        else { vec.normalize() }
        frenetFrameList[0].tangent.cross(vec, frenetFrameList[0].normal).normalize()
        frenetFrameList[0].tangent.cross(frenetFrameList[0].normal, frenetFrameList[0].binormal).normalize()

        frenetFrameList.windowed(2,1).forEach { (firstFrame, secondFrame) ->
            val b = Vector3f(firstFrame.tangent).cross(secondFrame.tangent)
            secondFrame.normal = firstFrame.normal.normalize()
            //if there is no substantial difference between two tangent vectors, the frenet frame need not to change
            if (b.length() > 0.00001f) {
                val firstNormal = firstFrame.normal
                b.normalize()
                val theta = acos(firstFrame.tangent.dot(secondFrame.tangent).coerceIn(-1f, 1f))
                val q = Quaternionf(AxisAngle4f(theta, b)).normalize()
                secondFrame.normal = q.transform(Vector3f(firstNormal)).normalize()
            }
            secondFrame.tangent.cross(secondFrame.normal, secondFrame.binormal).normalize()
        }
        return frenetFrameList.filterNot { it.binormal.toFloatArray().all { value -> value.isNaN() } &&
                it.normal.toFloatArray().all{ value -> value.isNaN()}}
    }

    companion object VerticesCalculation {
        /**
         * This function calculates the triangles for the rendering. It takes as a parameter
         * the [curveGeometry] List which contains all the baseShapes transformed and translated
         * along the curve.
         */
        fun calculateTriangles(curveGeometry: List<List<Vector3f>>, addCoverOrTop: Int = 2): Pair<ArrayList<Vector3f>, ArrayList<Vector3f>> {
            val verticesVectors = ArrayList<Vector3f>(curveGeometry.flatten().size * 6 + curveGeometry[0].size + 1)
            val normalVectors = ArrayList<Vector3f>(verticesVectors.size)
            if (curveGeometry.isEmpty()) {
                return Pair(verticesVectors, normalVectors)
            }
            if (addCoverOrTop == 0) {
                val newVerticesAndNormals = getCoverVertices(curveGeometry.first(), true)
                verticesVectors.addAll(newVerticesAndNormals.first)
                normalVectors.addAll(newVerticesAndNormals.second)
            }
            //if none of the lists in the curveGeometry differ in size, distinctBy leaves only one element
            if (curveGeometry.distinctBy { it.size }.size == 1) {
                val intermediateNormals = ArrayList<ArrayList<Vector3f>>(curveGeometry.flatten().size/curveGeometry[0].size)
                curveGeometry.dropLast(1).forEachIndexed { shapeIndex, shape ->

                    val intermediateNormalSection = ArrayList<Vector3f>(shape.size)

                    shape.dropLast(1).forEachIndexed { vertexIndex, _ ->
                        val triangle1Point1 = curveGeometry[shapeIndex][vertexIndex]
                        val triangle1Point2 = curveGeometry[shapeIndex][vertexIndex + 1]
                        val triangle1Point3 = curveGeometry[shapeIndex + 1][vertexIndex]
                        verticesVectors.add(triangle1Point1)
                        verticesVectors.add(triangle1Point2)
                        verticesVectors.add(triangle1Point3)

                        //normal calculation triangle 1
                        val normal1 = ((Vector3f(triangle1Point3).sub(Vector3f(triangle1Point1)))
                            .cross(Vector3f(triangle1Point2).sub(Vector3f(triangle1Point1))))
                        intermediateNormalSection.add(normal1)

                        val triangle2Point1 = curveGeometry[shapeIndex][vertexIndex + 1]
                        val triangle2Point2 = curveGeometry[shapeIndex + 1][vertexIndex + 1]
                        val triangle2Point3 = curveGeometry[shapeIndex + 1][vertexIndex]
                        verticesVectors.add(triangle2Point1)
                        verticesVectors.add(triangle2Point2)
                        verticesVectors.add(triangle2Point3)

                        //normal calculation triangle 2
                        val normal2 = ((Vector3f(triangle2Point3).sub(Vector3f(triangle2Point1)))
                            .cross(Vector3f(triangle2Point2).sub(Vector3f(triangle2Point1))))
                        intermediateNormalSection.add(normal2)
                    }

                    val triangle1Point1 = curveGeometry[shapeIndex][shape.lastIndex]
                    val triangle1Point2 = curveGeometry[shapeIndex][0]
                    val triangle1Point3 = curveGeometry[shapeIndex + 1][shape.lastIndex]
                    verticesVectors.add(triangle1Point1)
                    verticesVectors.add(triangle1Point2)
                    verticesVectors.add(triangle1Point3)

                    //normal calculation triangle 1
                    val normal1 = ((Vector3f(triangle1Point3).sub(Vector3f(triangle1Point1)))
                        .cross(Vector3f(triangle1Point2).sub(Vector3f(triangle1Point1))))
                    intermediateNormalSection.add(normal1)

                    val triangle2Point1 = curveGeometry[shapeIndex][0]
                    val triangle2Point2 = curveGeometry[shapeIndex + 1][0]
                    val triangle2Point3 = curveGeometry[shapeIndex + 1][shape.lastIndex]
                    verticesVectors.add(triangle2Point1)
                    verticesVectors.add(triangle2Point2)
                    verticesVectors.add(triangle2Point3)

                    //normal calculation triangle 2
                    val normal2 = ((Vector3f(triangle2Point3).sub(Vector3f(triangle2Point1)))
                        .cross(Vector3f(triangle2Point2).sub(Vector3f(triangle2Point1))))
                    intermediateNormalSection.add(normal2)

                    //add all triangle normals from this section
                    intermediateNormals.add(intermediateNormalSection)
                }
                normalVectors.addAll(computeNormals(intermediateNormals, curveGeometry.first().size))
            } else {
                throw IllegalArgumentException("The baseShapes must not differ in size!")
            }
            if (addCoverOrTop == 2) {
                val newVerticesAndNormals = getCoverVertices(curveGeometry.last(), false)
                verticesVectors.addAll(newVerticesAndNormals.first)
                normalVectors.addAll(newVerticesAndNormals.second)
            }
            return Pair(verticesVectors, normalVectors)
        }

        /**
        This function contains a generalized algorithm for the cover of a curve. It works like a pot and a lit. If you cover
        the bottom of a curve, the triangles should be arranged counterclockwise, for the top clockwise - this is signified
        by [ccw].
         */
        private fun getCoverVertices(list: List<Vector3f>, ccw: Boolean): Pair<ArrayList<Vector3f>, ArrayList<Vector3f>> {
            val size = list.size
            val verticesList = ArrayList<Vector3f>(size + (size / 2))
            val normalVectors = ArrayList<Vector3f>(verticesList.size/3)

            if (size >= 3) {
                val workList = ArrayList<Vector3f>(size)
                workList.addAll(list)

                //Ensures that the algorithm does not stop before the last triangle.
                if(size%2 == 0) { workList.add(list[0]) }

                val newList = ArrayList<Vector3f>((size + (size / 2)) / 2)
                workList.windowed(3, 2) { triangle ->
                    if (ccw) {
                        verticesList.add(triangle[0])
                        verticesList.add(triangle[2])
                        verticesList.add(triangle[1])

                        //compute normal
                        val normal = ((Vector3f(triangle[2]).sub(Vector3f(triangle[0])))
                            .cross(Vector3f(triangle[1]).sub(Vector3f(triangle[0])))).normalize()
                        for(i in 1..3) { normalVectors.add(normal) }
                    } else {
                        for (i in 0..2) {
                            verticesList.add(triangle[i])
                        }
                        //compute normal
                        val normal = ((Vector3f(triangle[0]).sub(Vector3f(triangle[2])))
                            .cross(Vector3f(triangle[1]).sub(Vector3f(triangle[0])))).normalize()
                        for(i in 1..3) { normalVectors.add(normal) }
                    }
                    newList.add(triangle[0])
                }

                //check if the recursion has come to an end
                if(newList.size >= 3) {
                    //to avoid gaps when the vertex number is odd
                    if (size % 2 == 1) {
                        newList.add(list.last())
                    }

                    val newVerticesAndNormals = getCoverVertices(newList, ccw)
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
        private fun computeNormals(intermediateNormals: ArrayList<ArrayList<Vector3f>>, shapeSize: Int): ArrayList<Vector3f> {
            //TODO allocate the size
            val normalsOfVertices = ArrayList<ArrayList<Vector3f>>()
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
                    shapeSize-1 -> {
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
                //TODO allocate size
                val allSectionNormals = ArrayList<Vector3f>()
                for(shapeIndex in 0 until shapeSize) {
                    val sectionIndex = shapeIndex*2
                    val vertexNormal = Vector3f()
                    when(shapeIndex) {
                        0-> {
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
        private fun orderNormals(verticesNormals: ArrayList<ArrayList<Vector3f>>): ArrayList<Vector3f> {
            //TODO allocate size
            val finalNormals = ArrayList<Vector3f>()
            verticesNormals.dropLast(1).forEachIndexed { shapeIndex, shape ->
                shape.dropLast(1).forEachIndexed { vertexIndex, _ ->

                    finalNormals.add(verticesNormals[shapeIndex][vertexIndex])
                    finalNormals.add(verticesNormals[shapeIndex][vertexIndex + 1])
                    finalNormals.add(verticesNormals[shapeIndex + 1][vertexIndex])

                    finalNormals.add(verticesNormals[shapeIndex][vertexIndex + 1])
                    finalNormals.add(verticesNormals[shapeIndex + 1][vertexIndex + 1])
                    finalNormals.add(verticesNormals[shapeIndex + 1][vertexIndex])
                }
                finalNormals.add(verticesNormals[shapeIndex][shape.lastIndex])
                finalNormals.add(verticesNormals[shapeIndex][0])
                finalNormals.add(verticesNormals[shapeIndex + 1][shape.lastIndex])

                finalNormals.add(verticesNormals[shapeIndex][0])
                finalNormals.add(verticesNormals[shapeIndex + 1][0])
                finalNormals.add(verticesNormals[shapeIndex + 1][shape.lastIndex])
            }
            return finalNormals
        }

    }

    /**
     * Each child of the curve must be, per definition, another Mesh. Therefore, this class turns a List of
     * vertices into a Mesh.
     */
    class PartialCurve(verticesVectors: ArrayList<Vector3f>, normalVectors: ArrayList<Vector3f>) : Mesh("PartialCurve") {
        init {
            geometry {
                vertices = BufferUtils.allocateFloat(verticesVectors.size * 3)
                verticesVectors.forEach {
                    vertices.put(it.toFloatArray())
                }
                vertices.flip()
                texcoords = BufferUtils.allocateFloat(verticesVectors.size * 2)
                normals = BufferUtils.allocateFloat(normalVectors.size*3*3)
                normalVectors.forEach {
                    normals.put(it.toFloatArray())
                }
                normals.flip()

                boundingBox = generateBoundingBox()
            }
            boundingBox = generateBoundingBox()

            //TODO delete

            val matBright = DefaultMaterial()
            matBright.diffuse  = Vector3f(0.0f, 1.0f, 0.0f)
            matBright.ambient  = Vector3f(1.0f, 1.0f, 1.0f)
            matBright.specular = Vector3f(1.0f, 1.0f, 1.0f)
            matBright.cullingMode = Material.CullingMode.None
            verticesVectors.forEachIndexed { index, vertex ->
                if(index%10 == 0) {
                    val normal = normalVectors[index]
                    val a = Arrow(Vector3f() - normal)  //shape of the vector itself
                    a.spatial {
                        position = vertex                 //position/base of the vector
                    }
                    a.addAttribute(Material::class.java, matBright)                  //usual stuff follows...
                    a.edgeWidth = 0.5f
                    this.addChild(a)
                }
            }

        }
    }
}
