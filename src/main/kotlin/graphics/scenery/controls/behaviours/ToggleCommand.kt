package graphics.scenery.controls.behaviours

import graphics.scenery.utils.LazyLogger
import org.scijava.ui.behaviour.ClickBehaviour
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException

/**
 * Toggle command class. Enables to call a parameter-free method of an instance
 * by the press of a button.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[receiver] The receiving object
 * @property[method] The name of the method to invoke
 */
class ToggleCommand(private val receiver: Any, private val method: String) : ClickBehaviour {

    private val logger by LazyLogger()

    /**
     * This function is called upon arrival of an event that concerns
     * this behaviour. It will execute the given method on the given object instance.
     *
     * @param[x] x position in window (unused)
     * @param[y] y position in window (unused)
     */
    override fun click(x: Int, y: Int) {
        try {
            val m = receiver.javaClass.getMethod(method)
            m.invoke(receiver)
        } catch(e: NoSuchMethodException) {
            logger.warn("Method $method not found for ${receiver.javaClass.simpleName}")
        } catch(e: InvocationTargetException) {
            logger.warn("Method $method for ${receiver.javaClass.simpleName} threw an error: $e:")
            e.printStackTrace()
        }
    }
}
