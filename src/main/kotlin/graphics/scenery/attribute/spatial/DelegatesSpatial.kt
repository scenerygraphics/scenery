package graphics.scenery.attribute.spatial

import graphics.scenery.Node
import graphics.scenery.attribute.HasDelegationType

interface DelegatesSpatial: Node, HasDelegationType {

    fun getDelegateSpatial(): Spatial?

    override fun ifSpatial(block: Spatial.() -> Unit): Spatial? {
        val delegateSpatial = getDelegateSpatial()
        delegateSpatial?.block()
        return delegateSpatial
    }
}
