package scenery.controls.behaviours

import org.scijava.ui.behaviour.ClickBehaviour
import scenery.Camera

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class MovementCommand(private val name: String, private val direction: String, private val cam: Camera) : ClickBehaviour {

    private var speed = 0.1f

    constructor(name: String, direction: String, cam: Camera, speed: Float): this(name, direction, cam) {
        this.speed = speed;
    }

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
