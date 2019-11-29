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
    /**
     * Returns a single point on one of the three randomly-determined layers. Points on a single
     * circle are equidistributed according to [index] out of [pointsPerCircle].
     */
    override fun generatePoint(cam : Camera, index : Int, totalPointCount : Int): CalibrationPointGenerator.CalibrationPoint {
        val origin = 0.0f
        val radius = 0.4f
        val layer = kotlin.random.Random(System.nanoTime()).nextInt(0, layerCount)

        val z = -1.0f * (0.75f + layer * 1.0f)

        val v = if (index == 0) {
            GLVector(origin, origin, z)
        } else {
            GLVector(
                origin + radius * cos(2 * PI.toFloat() * index.toFloat() / pointsPerCircle),
                origin + radius * sin(2 * PI.toFloat() * index.toFloat() / pointsPerCircle),
                z
            )
        }

        // Pupil's world-space calibration expects points in mm units, while
        // scenery's units are in meters
        return CalibrationPointGenerator.CalibrationPoint(GLVector(v.x(), v.y(), -1.0f * v.z()) * 1000.0f, v)
    }
}
