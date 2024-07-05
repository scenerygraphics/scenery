package graphics.scenery.geometry.curve

import graphics.scenery.backends.Renderer.Companion.logger
import graphics.scenery.geometry.Spline
import graphics.scenery.utils.extensions.toFloatArray
import graphics.scenery.utils.lazyLogger
import org.joml.*
import kotlin.math.acos

/**
 * Curve interface for curves whose base shapes need to be oriented along the curve using [FrenetFrame]s.
 */
interface FrenetCurve: Curve {
    /**
     * Frenet frame for each spline point
     */
    val frenetFrames: () -> List<FrenetFrame>

    /**
     * The frenet frame calculation needs an initial normal vector to be well-defined.
     */
    val firstPerpendicularVector: Vector3f

    /**
     * Transforms the base shapes with help of the frenet frames.
     */
    fun transformedBaseShapes(shapes: SegmentedShapeList, frenetFrames: List<FrenetFrame>): SegmentedShapeList {

        if (frenetFrames.isEmpty()) {
            logger.warn("The spline provided for the Curve is empty.")
        } else if (frenetFrames.size != shapes.size) {
            logger.warn("Not the same amount of shapes and frenet frames!")
        }
        val bases = frenetFrames.map { (t, n, b, tr) ->
            val inverseMatrix = Matrix3f(
                b.x(), n.x(), t.x(),
                b.y(), n.y(), t.y(),
                b.z(), n.z(), t.z()
            ).invert()
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
                tr.x(), tr.y(), tr.z(), 1f
            )
        }
        val transformedBaseShapes = ArrayList<Shape>(bases.size)
        bases.forEachIndexed { index, base ->
            val shape = if (shapes.size == 1) {
                shapes.first()
            } else {
                shapes[index]
            }
            val transformedVertices = ArrayList<Vertex>(shape.vertices.size)
            shape.vertices.forEach { point ->
                val transformedPoint = Vector3f()
                val transformedNormal = Vector3f()
                base.transformPosition(point.v, transformedPoint)
                base.transformDirection(point.n, transformedNormal)
                val v = Vertex(transformedPoint, transformedNormal.normalize(), point.uv)
                transformedVertices.add(v)
            }
            transformedBaseShapes.add(Shape(transformedVertices))
        }
        return transformedBaseShapes
    }

    /**
     * Companion object for [FrenetCurve] with utility functions.
     */
    companion object {
        /**
         * This function returns the frenet frames along the curve. This is essentially a new
         * coordinate system which represents the form of the curve. For details concerning the
         * calculation see: http://www.cs.indiana.edu/pub/techreports/TR425.pdf
         */
        fun computeFrenetFrames(
            spline: Spline,
            firstPerpendicularVector: Vector3f = Vector3f(0f, 0f, 0f)
        ): List<FrenetFrame> {
            val chain = spline.splinePoints()
            val curve = chain as ArrayList
            val frenetFrameList = ArrayList<FrenetFrame>(curve.size)

            if(curve.isEmpty()) {
                return frenetFrameList
            }

            //adds all the tangent vectors
            curve.forEachIndexed { index, _ ->
                val frenetFrame = FrenetFrame(getTangent(index, chain), Vector3f(), Vector3f(), curve[index])
                frenetFrameList.add(frenetFrame)
            }
            var min = Float.MIN_VALUE
            val vec = Vector3f(0f, 0f, 0f)
            vec.set(firstPerpendicularVector)
            if(vec == Vector3f(0f, 0f, 0f)) {
                val normal = Vector3f()
                if(frenetFrameList[0].tangent.x() <= min) {
                    min = frenetFrameList[0].tangent.x()
                    normal.set(1f, 0f, 0f)
                }
                if(frenetFrameList[0].tangent.y() <= min) {
                    min = frenetFrameList[0].tangent.y()
                    normal.set(0f, 1f, 0f)
                }
                if(frenetFrameList[0].tangent.z() <= min) {
                    normal.set(0f, 0f, 1f)
                } else {
                    normal.set(1f, 0f, 0f).normalize()
                }
                frenetFrameList[0].tangent.cross(normal, vec).normalize()
            } else {
                vec.normalize()
            }
            frenetFrameList[0].tangent.cross(vec, frenetFrameList[0].normal).normalize()
            frenetFrameList[0].tangent.cross(frenetFrameList[0].normal, frenetFrameList[0].binormal).normalize()

            frenetFrameList.windowed(2, 1).forEach { (firstFrame, secondFrame) ->
                val b = Vector3f(firstFrame.tangent).cross(secondFrame.tangent)
                secondFrame.normal.set(firstFrame.normal.normalize())
                //if there is no substantial difference between two tangent vectors, the frenet frame need not change
                if(b.length() > 0.00001f) {
                    val firstNormal = firstFrame.normal
                    b.normalize()
                    val theta = acos(firstFrame.tangent.dot(secondFrame.tangent).coerceIn(-1f, 1f))
                    val q = Quaternionf(AxisAngle4f(theta, b)).normalize()
                    secondFrame.normal.set(q.transform(Vector3f(firstNormal)).normalize())
                }
                secondFrame.tangent.cross(secondFrame.normal, secondFrame.binormal).normalize()
            }
            return frenetFrameList.filterNot {
                it.binormal.toFloatArray().all { value -> value.isNaN() } &&
                        it.normal.toFloatArray().all { value -> value.isNaN() }
            }
        }

        /**
         * This function calculates the tangent at a given index.
         * [i] index of the curve (not the geometry!)
         */
        private fun getTangent(i: Int, chain: List<Vector3f>): Vector3f {
            val tangent = Vector3f()
            if(chain.size >= 3) {
                when(i) {
                    0 -> {
                        ((chain[1].sub(chain[0], tangent)).normalize())
                    }

                    1 -> {
                        ((chain[2].sub(chain[0], tangent)).normalize())
                    }

                    chain.lastIndex - 1 -> {
                        ((chain[i + 1].sub(chain[i - 1], tangent)).normalize())
                    }

                    chain.lastIndex -> {
                        ((chain[i].sub(chain[i - 1], tangent)).normalize())
                    }

                    else -> {
                        chain[i + 1].sub(chain[i - 1], tangent).normalize()
                    }
                }
                return tangent
            } else {
                val frenetLogger by lazyLogger()
                frenetLogger.error("The chain size is too small.")
                return tangent
            }
        }
    }
}
