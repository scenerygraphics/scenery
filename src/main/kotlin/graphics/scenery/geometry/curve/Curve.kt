package graphics.scenery.geometry.curve

import graphics.scenery.geometry.Spline
import org.joml.*

/**
 * Curve implementing the baseShapes with a lambda, allowing for maximal flexibility when choosing the baseShapes
 */

class Curve (spline: Spline, firstPerpendicularVector: Vector3f = Vector3f(0f, 0f, 0f),
             partitionAlongControlpoints: Boolean = true, baseShape: () -> List<List<Vector3f>>):
    AbstractCurve(spline, firstPerpendicularVector, partitionAlongControlpoints) {

    //invokation of the baseShapes
    override val baseShapes: List<List<Vector3f>> = baseShape.invoke()

}
