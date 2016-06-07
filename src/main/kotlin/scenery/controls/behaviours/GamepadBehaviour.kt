package scenery.controls.behaviours

import net.java.games.input.Component
import org.scijava.ui.behaviour.Behaviour

interface GamepadBehaviour : Behaviour {
    val axis: List<Component.Identifier>

    fun axisEvent(axis: Component.Identifier, value: Float)
}

