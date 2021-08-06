package graphics.scenery.attribute.geometry

import graphics.scenery.Node
import graphics.scenery.attribute.HasDelegationType

interface DelegatesGeometry: Node, HasDelegationType {

    fun getDelegateGeometry(): Geometry?

    override fun ifGeometry(block: Geometry.() -> Unit): Geometry? {
        val delegateGeometry = getDelegateGeometry()
        delegateGeometry?.block()
        return delegateGeometry
    }
}
