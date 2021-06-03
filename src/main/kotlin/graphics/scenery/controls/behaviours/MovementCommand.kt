package graphics.scenery.controls.behaviours

import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.scijava.ui.behaviour.ClickBehaviour
import org.slf4j.Logger
import kotlin.reflect.KProperty

/**
 * Movement Command class. Moves a given camera in the given direction.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[direction] The direction of movement as string. Can be forward/back/left/right/up/down.
 * @property[n] The [Node] this behaviour affects.
 */
open class MovementCommand(private val direction: String, private var n: () -> Node?) : ClickBehaviour {

    private val node: Node? by NodeDelegate()
    private val logger: Logger by LazyLogger()

    protected inner class NodeDelegate {
        /** Returns the [graphics.scenery.Node] resulting from the evaluation of [n] */
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Node? {
            return n.invoke()
        }

        /** Setting the value is not supported */
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Node?) {
            throw UnsupportedOperationException()
        }
    }

    /** Movement speed multiplier */
    var speed = 0.5f

    /**
     * Additional constructor to directly adjust movement speed.
     *
     * @param[direction] The direction of movement as string. Can be forward/back/left/right/up/down.
     * @param[n] The [Node] this behaviour affects.
     * @param[speed] The speed multiplier for movement.
     */
    constructor(direction: String, n: () -> Node?, speed: Float): this(direction, n) {
        this.speed = speed
    }

    /**
     * This function is triggered upon arrival of a click event that concerns
     * this behaviour. The camera is then moved in the corresponding direction.
     * this behaviour. The camera is then moved in the corresponding direction.
     */
    @Synchronized override fun click(x: Int, y: Int) {
        // see if the node is a camera, if not, try to find the active observer, and return
        // if that could not be found as well
        val axisProvider = node as? Camera ?: node?.getScene()?.findObserver() ?: return

        node?.let { node ->
            if (node.lock.tryLock()) {
                when (direction) {
                    "forward" -> node.position = node.position + axisProvider.forward * speed * axisProvider.deltaT
                    "back" -> node.position = node.position - axisProvider.forward * speed * axisProvider.deltaT
                    "left" -> node.position = node.position - axisProvider.right * speed * axisProvider.deltaT
                    "right" -> node.position = node.position + axisProvider.right * speed * axisProvider.deltaT
                    "up" -> node.position = node.position + axisProvider.up * speed * axisProvider.deltaT
                    "down" -> node.position = node.position - axisProvider.up * speed * axisProvider.deltaT
                }

                node.lock.unlock()
            }
        }
    }
}
