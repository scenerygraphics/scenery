package scenery.controls.behaviours

import net.java.games.input.Component
import scenery.Camera

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class GamepadMovementControl(private val name: String,
                             override val axis: List<Component.Identifier>,
                             private val cam: Camera) : GamepadBehaviour {
    protected val speedMultiplier = 0.8f
    protected val threshold = 0.05f

    @Synchronized
    override fun axisEvent(axis: Component.Identifier, value: Float) {
        if(Math.abs(value) < threshold) {
            return
        }

        when (axis) {
            Component.Identifier.Axis.Y -> { cam.position = cam.position + cam.forward * -1.0f * value * speedMultiplier }
            Component.Identifier.Axis.X -> { cam.position = cam.position + cam.forward.cross(cam.up).normalized * value * speedMultiplier }
        }

//        System.err.println("Camera.position=${cam.position.x()}/${cam.position.y()}/${cam.position.z()}")
    }

}

