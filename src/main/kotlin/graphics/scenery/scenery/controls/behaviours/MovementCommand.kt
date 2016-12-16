package graphics.scenery.scenery.controls.behaviours

import org.scijava.ui.behaviour.ClickBehaviour
import graphics.scenery.scenery.Camera

/**
 * Movement Command class. Moves a given camera in the given direction.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[name] The name of the behaviour
 * @property[direction] The direction of movement as string. Can be forward/back/left/right/up/down.
 * @property[cam] The camera this behaviour affects.
 */
class MovementCommand(private val name: String, private val direction: String, private val cam: Camera) : ClickBehaviour {

    /** Movement speed multiplier */
    private var speed = 0.1f

    /**
     * Additional constructor to directly adjust movement speed.
     *
     * @param[name] The name of the behaviour
     * @param[direction] The direction of movement as string. Can be forward/back/left/right/up/down.
     * @param[cam] The camera this behaviour affects.
     * @param[speed] The speed multiplier for movement.
     */
    constructor(name: String, direction: String, cam: Camera, speed: Float): this(name, direction, cam) {
        this.speed = speed;
    }

    /**
     * This function is triggered upon arrival of a click event that concerns
     * this behaviour. The camera is then moved in the corresponding direction.
     * this behaviour. The camera is then moved in the corresponding direction.
     */
    override fun click(x: Int, y: Int) {
        when (direction) {
            "forward" -> cam.position = cam.position + cam.forward * speed
            "back" -> cam.position = cam.position - cam.forward * speed
            "left" -> cam.position = cam.position - cam.forward.cross(cam.up).normalized * speed
            "right" -> cam.position = cam.position + cam.forward.cross(cam.up).normalized * speed
            "up" -> cam.position = cam.position + cam.up * speed
            "down" -> cam.position = cam.position + cam.up * -1.0f * speed
        }
    }
}
