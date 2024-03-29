package graphics.scenery.attribute.renderable

import graphics.scenery.Node
import graphics.scenery.attribute.HasDelegationType

/**
 * Node type that enables delegation of rendering. For rendering, not the node itself will be drawn,
 * but the node referred as [delegateRenderable]. A [delegationType] can be selected to choose whether the delegate
 * will be drawn for all nodes that refer to it, or only once.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
interface DelegatesRenderable: Node, HasDelegationType {

    fun getDelegateRenderable(): Renderable?

    override fun ifRenderable(block: Renderable.() -> Unit): Renderable? {
        val delegateRenderable = getDelegateRenderable()
        delegateRenderable?.block()
        return delegateRenderable
    }
}
