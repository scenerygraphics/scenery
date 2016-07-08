package scenery.controls.behaviours

import net.java.games.input.Component
import org.scijava.ui.behaviour.Behaviour

/**
 * Gamepad Axis Event Behaviour Interface
 *
 * Tracks the given axis of a gamepad
 */
interface GamepadBehaviour : Behaviour {
    /** The axis of the controller this behaviour is assigned to */
    val axis: List<Component.Identifier>

    /**
     * This function is called when the given axis is triggered
     *
     * @param[axis] The gamepad axis.
     * @param[value] The absolute position of the axis.
     */
    fun axisEvent(axis: Component.Identifier, value: Float)
}

