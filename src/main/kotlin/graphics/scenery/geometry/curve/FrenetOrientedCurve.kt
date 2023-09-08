package graphics.scenery.geometry.curve

import graphics.scenery.backends.Renderer.Companion.logger
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Curve interface for curves whose base shapes need to be oriented along the curve.
 */
interface FrenetOrientedCurve: CurveInterfaceNew {
    /**
     * Frenet frame for each spline point
     */
    val frenetFrames: () -> List<FrenetFrame>

    /**
     * The frenet frame calculation needs an initial normal vector to be well-defined.
     */
    val firstPerpendicularVector3f: Vector3f

    /**
     * Transforms the base shapes with help of the frenet frames.
     */
    fun transformedBaseShapes(shapes: List<List<Vector3f>>, frenetFrames: List<FrenetFrame>): List<List<Vector3f>> {

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
        val transformedBaseShapes = ArrayList<List<Vector3f>>(bases.size)
        bases.forEachIndexed { index, base ->
            val shape = if (shapes.size == 1) {
                shapes.first()
            } else {
                shapes[index]
            }
            val transformedShape = ArrayList<Vector3f>(shape.size)
            shape.forEach { point ->
                val transformedPoint = Vector3f()
                base.transformPosition(point, transformedPoint)
                transformedShape.add(transformedPoint)
            }
            transformedBaseShapes.add(transformedShape)
        }
        return transformedBaseShapes
    }
}
