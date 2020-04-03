package graphics.scenery.controls.eyetracking

import cleargl.GLVector
import graphics.scenery.Camera

/**
 * Interface to generate calibration points for eye trackers
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
interface CalibrationPointGenerator {
    /**
     * The CalibrationPoint data class holds a [local] point representation (which might be 2D
     * for screen-space calibration), and w [world]-space representation, which is always 3D.
     */
    data class CalibrationPoint(val local: GLVector, val world: GLVector)

    /**
     * Generate a single [CalibrationPoint], with [index] out of [totalPointCount].
     */
    fun generatePoint(cam: Camera, index: Int, totalPointCount: Int): CalibrationPoint
}
