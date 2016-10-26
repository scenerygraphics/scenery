package scenery.controls

import net.java.games.input.ControllerListener
import org.scijava.ui.behaviour.BehaviourMap
import org.scijava.ui.behaviour.InputTriggerMap

/**
 * Created by ulrik on 10/26/2016.
 */
interface MouseAndKeyHandler: ControllerListener {
    fun setInputMap(inputMap: InputTriggerMap)
    fun setBehaviourMap(behaviourMap: BehaviourMap)
}
