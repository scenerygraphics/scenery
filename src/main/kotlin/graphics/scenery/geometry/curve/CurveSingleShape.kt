package graphics.scenery.geometry.curve

import graphics.scenery.geometry.Spline
import org.joml.*

/**
 * Curve implementing the baseShapes with only a single shape, allowing for more efficient storage
 */

class CurveSingleShape (spline: Spline, baseShape: List<Vector3f>,
                        firstPerpendicularVector: Vector3f = Vector3f(0f, 0f, 0f),
                        partitionAlongControlpoints: Boolean = true):
    AbstractCurve(spline, firstPerpendicularVector, partitionAlongControlpoints, listOf(baseShape))
