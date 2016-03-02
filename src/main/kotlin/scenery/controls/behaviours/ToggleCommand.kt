package scenery.controls.behaviours

import org.scijava.ui.behaviour.ClickBehaviour

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ToggleCommand(private val name: String, private val receiver: Any, private val method: String) : ClickBehaviour {

    override fun click(x: Int, y: Int) {
        val m = receiver.javaClass.getMethod(method)
        m.invoke(receiver)
    }
}
