package graphics.scenery.effectors

import graphics.scenery.Node
import graphics.scenery.volumes.Volume

/**
 * Volume effector class
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
open class VolumeEffector : Node("VolumeEffector") {
    var active: Boolean = false
        private set

    var activeVolume: Node? = null
        private set

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
