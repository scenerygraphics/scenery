package graphics.scenery.controls.behaviours

import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread

/**
 * [ClickBehaviour] that waits [timeout] for confirmation by re-executing the behaviour.
 * Executes [armedAction] on first invocation, and [confirmAction] on second invocation, if
 * it happens within [timeout].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class ConfirmableClickBehaviour(val armedAction: (Long) -> Any, val confirmAction: (Long) -> Any, var timeout: Long = 3000): ClickBehaviour {
    /** Whether the action is armed at the moment. Action becomes disarmed after [timeout]. */
    private var armed: Boolean = false

    /**
     * Action fired at position [x]/[y]. Parameters not used in VR actions.
     */
    override fun click(x : Int, y : Int) {
        if(!armed) {
            armed = true
            armedAction.invoke(timeout)

            thread {
                Thread.sleep(timeout)
                armed = false
            }
        } else {
            confirmAction.invoke(timeout)
        }
    }
}
