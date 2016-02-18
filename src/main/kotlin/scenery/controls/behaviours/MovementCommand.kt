package scenery.controls.behaviours

import org.scijava.ui.behaviour.ClickBehaviour
import scenery.Camera

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class MovementCommand(private val name: String, private val direction: String, private val cam: Camera) : ClickBehaviour {

    override fun click(x: Int, y: Int) {
        when (direction) {
            "forward" -> cam.position = cam.position + cam.forward * 1.0f
            "back" -> cam.position = cam.position - cam.forward * 1.0f
            "left" -> cam.position = cam.position - cam.forward.cross(cam.up).normalized * 1.0f
            "right" -> cam.position = cam.position + cam.forward.cross(cam.up).normalized * 1.0f
        }
    }
}
