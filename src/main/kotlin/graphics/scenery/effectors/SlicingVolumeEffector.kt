package graphics.scenery.effectors

import cleargl.GLVector
import graphics.scenery.Node
import graphics.scenery.Plane

/**
 * Slicing volume effector
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class SlicingVolumeEffector : VolumeEffector() {
    /** Proxy plane for slicing */
    override var proxy: Node = Plane(GLVector(1.0f, 1.0f, 1.0f))
}
