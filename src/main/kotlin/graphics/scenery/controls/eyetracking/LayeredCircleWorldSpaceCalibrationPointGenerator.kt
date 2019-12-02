package graphics.scenery.controls.eyetracking

import cleargl.GLVector
import graphics.scenery.Camera
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates a calibration points on [layerCount] layered circles in world space.
 * A maximum of [pointsPerCircle] are generated per-circle.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class LayeredCircleWorldSpaceCalibrationPointGenerator(val layerCount: Int = 3, val pointsPerCircle: Int = 5) : CalibrationPointGenerator {
    data class RadiusDistance(val radius: Float, val distance: Float)
    val radiiAndDistances = hashMapOf<Int, RadiusDistance>(
        0 to RadiusDistance(0.18f, -0.6f),
        1 to RadiusDistance(0.24f, -1.0f),
        2 to RadiusDistance(0.42f, -2.0f)
    )

    /**
     * Returns a single point on one of the three randomly-determined layers. Points on a single
     * circle are equidistributed according to [index] out of [pointsPerCircle].
     */
    override fun generatePoint(cam : Camera, index : Int, totalPointCount : Int): CalibrationPointGenerator.CalibrationPoint {
        val origin = 0.0f
        val layer = index % layerCount

        val (radius, z) = radiiAndDistances.getOrDefault(layer, RadiusDistance(0.5f, 1.0f))

        val v = if (index < 3) {
            GLVector(origin, origin, z)
        } else {
            GLVector(
                origin + radius * cos(2 * PI.toFloat() * index.toFloat() / pointsPerCircle),
                origin + radius * sin(2 * PI.toFloat() * index.toFloat() / pointsPerCircle),
                z
            )
        }

        return CalibrationPointGenerator.CalibrationPoint(v, v)
    }
}
