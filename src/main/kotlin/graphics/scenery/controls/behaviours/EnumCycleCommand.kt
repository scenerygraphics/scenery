package graphics.scenery.controls.behaviours

import graphics.scenery.utils.LazyLogger
import org.scijava.ui.behaviour.ClickBehaviour
import java.lang.reflect.InvocationTargetException

/**
 * Enum advance command class. Enables to call a single-parameter method with a successive list of
 * enum [values] by the press of a button. The value used for calling is incremented with each call
 * and wrapped upon arriving at the end of the list. The [method] called should provide the user some
 * feedback about the change made, as this command does not (although a debug message is emitted,
 * containing the receiver, method, and current value).
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[values] The enum properties to use.
 * @property[receiver] The receiving object.
 * @property[method] The name of the single-parameter method to invoke.
 */
class EnumCycleCommand<T: Enum<*>>(private val enumClass: Class<T>,
                                   private val receiver: Any,
                                   private val method: String) : ClickBehaviour {

    private val logger by LazyLogger()
    private var currentIndex = 0

    /**
     * This function is called upon arrival of an event that concerns
     * this behaviour. It will execute the given method on the given object instance.
     *
     * @param[x] x position in window (unused)
     * @param[y] y position in window (unused)
     */
    override fun click(x: Int, y: Int) {
        val values = enumClass.enumConstants
        if(values.isEmpty()) {
            return
        }

        try {
            val clazz = values.get(0)::class.java
            val m = receiver.javaClass.getMethod(method, clazz)

            m.invoke(receiver, values[currentIndex])
            logger.debug("Ran ${receiver.javaClass.simpleName}.$method(${values[currentIndex].name})")

            currentIndex = (currentIndex + 1) % values.size
        } catch(e: NoSuchMethodException) {
            logger.warn("Method $method not found for ${receiver.javaClass.simpleName}")
        } catch(e: InvocationTargetException) {
            logger.warn("Method $method for ${receiver.javaClass.simpleName} threw an error: $e:")
            e.printStackTrace()
        }
    }
}
