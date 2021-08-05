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

    @Throws(IllegalStateException::class)
    fun spatial(block: Spatial.() -> Unit): Spatial {
        val delegateSpatial = getDelegateSpatial()
            ?: throw IllegalStateException(name + ": delegates spatial properties, but the delegate is null")
        delegateSpatial.block()
        return delegateSpatial
    }
}
