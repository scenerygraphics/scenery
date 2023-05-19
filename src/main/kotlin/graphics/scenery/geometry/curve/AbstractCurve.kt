package graphics.scenery.geometry.curve

import graphics.scenery.Mesh
import graphics.scenery.geometry.Spline
import org.joml.*

/**
 * Constructs a geometry along the calculates points of a Spline.
 * The number n corresponds to the number of segments you wish to have between your control points.
 * The spline and the baseShape lambda must both have the same number of elements, otherwise, the curve is no
 * longer well-defined. Concerning the individual baseShapes, no lines must cross for the body of the curve to
 * be visualized flawlessly. Furthermore, all baseShapes ought to be convex.
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 * @param [baseShape] a lambda which returns all the baseShapes along the curve
 * @param [spline] the spline along which the geometry will be rendered
 * @param [partitionAlongControlpoints] flag to indicate that the curve should be divided into subcurves, one for each
 * controlpoint, note that this option prohibits the use of different baseShapes
 */

abstract class AbstractCurve (spline: Spline,
             firstPerpendicularVector: Vector3f = Vector3f(0f, 0f, 0f),
             partitionAlongControlpoints: Boolean = true, baseShapes: List<List<Vector3f>>): Mesh("CurveGeometry"), CurveInterface {

    private val countList = ArrayList<Int>(50).toMutableList()
    private val frenetFrameCalculator = FrenetFrameCalculator(spline, firstPerpendicularVector)
    val frames = frenetFrameCalculator.computeFrenetFrames()
    private val sectionVertices = spline.verticesCountPerSection()


    /*
     * This function renders the spline.
     * [baseShape] It takes a lambda as a parameter, which is the shape of the
     * curve.
     * If you choose, for example, to have a square as a base shape, your spline will look like
     * a banister.
     */
    init {
        if (frames.isEmpty()) {
            logger.warn("The spline provided for the Curve is empty.")
        }
        val bases = frames.map { (t, n, b, tr) ->
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
        val transformedBaseShapes = ArrayList<List<Vector3f>>(bases.size)
        bases.forEachIndexed {index, base ->
            val shape = if(baseShapes.size==1) { baseShapes.first() } else { baseShapes[index] }
            val transformedShape = ArrayList<Vector3f>(shape.size)
            shape.forEach { point ->
                val transformedPoint = Vector3f()
                base.transformPosition(point, transformedPoint)
                transformedShape.add(transformedPoint)
            }
            transformedBaseShapes.add(transformedShape)
        }

        if(partitionAlongControlpoints) {
            val subShapes = transformedBaseShapes.windowed(sectionVertices+1, sectionVertices+1, true)
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
                    subShapes.lastIndex -> {
                        CurveCover.Bottom
                    }
                    else -> {
                        CurveCover.None
                    }
                }
                val trianglesAndNormals = CurveInterface.calculateTriangles(arrayList, cover = cover)
                val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
                this.addChild(partialCurve)
            }
        }
        else {
            //case of a single baseShape by construction
            if (baseShapes.size == 1 && bases.size > 1) {
                val trianglesAndNormals = CurveInterface.calculateTriangles(transformedBaseShapes, cover = CurveCover.Both)
                val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
                this.addChild(partialCurve)
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
                //if there is only one baseShape, the countlist will have size one, making the computation simpler
                if (countList.size == 1) {
                    val trianglesAndNormals = CurveInterface.calculateTriangles(transformedBaseShapes, cover = CurveCover.Both)
                    val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
                    this.addChild(partialCurve)
                } else {
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
                        val cover = if (index == 0) {
                            CurveCover.Top
                        } else if (index == countList.size - 1) {
                            CurveCover.Bottom
                        } else {
                            CurveCover.None
                        }
                        val trianglesAndNormals = CurveInterface.calculateTriangles(partialCurveGeometry, cover = cover)
                        val partialCurve = PartialCurve(trianglesAndNormals.first, trianglesAndNormals.second)
                        this.addChild(partialCurve)
                    }
                }
            }
        }
    }
}
