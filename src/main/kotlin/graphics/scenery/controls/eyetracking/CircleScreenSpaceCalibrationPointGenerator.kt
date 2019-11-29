package graphics.scenery.controls.eyetracking

import cleargl.GLVector
import graphics.scenery.Camera
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Screen-space calibration point generator that generates [CalibrationPointGenerator.CalibrationPoint]s
 * equidistributed on a circle.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class CircleScreenSpaceCalibrationPointGenerator : CalibrationPointGenerator {
    /**
     * Generates a single point on a circle, with [index] out of [totalPointCount].
     */
    override fun generatePoint(cam : Camera, index : Int, totalPointCount : Int): CalibrationPointGenerator.CalibrationPoint {
        val origin = 0.5f
        val radius = 0.3f

        val v = if(index == 0) {
            GLVector(origin, origin)
        } else {
            GLVector(
                origin + radius * cos(2 * PI.toFloat() * index.toFloat()/totalPointCount),
                origin + radius * sin(2 * PI.toFloat() * index.toFloat()/totalPointCount))
        }
        return CalibrationPointGenerator.CalibrationPoint(v, cam.viewportToWorld(GLVector(v.x()*2.0f-1.0f, v.y()*2.0f-1.0f), offset = 0.5f))
    }
}
