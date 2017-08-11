package graphics.scenery.controls.behaviours

import graphics.scenery.Camera
import org.scijava.ui.behaviour.ClickBehaviour
import graphics.scenery.Node
import kotlin.reflect.KProperty

/**
 * Movement Command class. Moves a given camera in the given direction.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[name] The name of the behaviour
 * @property[direction] The direction of movement as string. Can be forward/back/left/right/up/down.
 * @property[n] The [Node] this behaviour affects.
 */
class MovementCommand(private val name: String, private val direction: String, private var n: () -> Node?) : ClickBehaviour {

    private val node: Node? by NodeDelegate()

    inner class NodeDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Node? {
            return n.invoke()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Node?) {
            throw UnsupportedOperationException()
        }
    }

    /** Movement speed multiplier */
    private var speed = 0.01f

    /**
     * Additional constructor to directly adjust movement speed.
     *
     * @param[name] The name of the behaviour
     * @param[direction] The direction of movement as string. Can be forward/back/left/right/up/down.
     * @param[n] The [Node] this behaviour affects.
     * @param[speed] The speed multiplier for movement.
     */
    constructor(name: String, direction: String, n: () -> Node?, speed: Float): this(name, direction, n) {
        this.speed = speed
    }

    /**
     * This function is triggered upon arrival of a click event that concerns
     * this behaviour. The camera is then moved in the corresponding direction.
     * this behaviour. The camera is then moved in the corresponding direction.
     */
    @Synchronized override fun click(x: Int, y: Int) {
        node?.let { node ->
            if (node.lock.tryLock()) {
                if(node is Camera) {
                    when (direction) {
                        "forward" -> node.position = node.position + node.forward * speed * node.deltaT
                        "back" -> node.position = node.position - node.forward * speed * node.deltaT
                        "left" -> node.position = node.position - node.forward.cross(node.up).normalized * speed * node.deltaT
                        "right" -> node.position = node.position + node.forward.cross(node.up).normalized * speed * node.deltaT
                        "up" -> node.position = node.position + node.up * speed * node.deltaT
                        "down" -> node.position = node.position + node.up * -1.0f * speed * node.deltaT
                    }
                } else {
                    // need to find a camera; if we can't find one, just return
                    node.getScene()?.findObserver()?.let { cam ->
                        when (direction) {
                            "forward" -> node.position = node.position + cam.forward * speed * cam.deltaT
                            "back" -> node.position = node.position - cam.forward * speed * cam.deltaT
                            "left" -> node.position = node.position - cam.forward.cross(cam.up).normalized * speed * cam.deltaT
                            "right" -> node.position = node.position + cam.forward.cross(cam.up).normalized * speed * cam.deltaT
                            "up" -> node.position = node.position + cam.up * speed * cam.deltaT
                            "down" -> node.position = node.position + cam.up * -1.0f * speed * cam.deltaT
                        }
                    }
                }

                node.lock.unlock()
            }
        }
    }
}
