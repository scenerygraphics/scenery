package graphics.scenery.effectors

import graphics.scenery.Node
import graphics.scenery.volumes.Volume

/**
 * Volume effector class
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
open class VolumeEffector : Node("VolumeEffector") {
    /** Whether this effector node is currently active */
    var active: Boolean = false
        private set

    /** The volume this effector is associated with */
    var activeVolume: Node? = null
        private set

    /** Proxy node to display e.g. auxiliary geometry. */
    open var proxy = Node()

    init {
        update.add {
            getScene()?.let {
                it.discover(it, { it is Volume }).forEach { node ->
                    if (proxy.intersects(node)) {
                        activeVolume = node
                    }
                }
            }
        }
    }
}
