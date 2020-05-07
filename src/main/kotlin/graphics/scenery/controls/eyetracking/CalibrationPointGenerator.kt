package graphics.scenery.controls.eyetracking

import org.joml.Vector3f
import graphics.scenery.Camera
import org.joml.Vector2f

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
    data class CalibrationPoint(val local: Vector3f, val world: Vector3f)

    /**
     * Generate a single [CalibrationPoint], with [index] out of [totalPointCount].
     */
    fun generatePoint(cam: Camera, index: Int, totalPointCount: Int): CalibrationPoint
}
