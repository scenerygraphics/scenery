package graphics.scenery

/**
 * Delegation type class.
 *
 * Delegations can be handled either as [OncePerDelegate], meaning that even if many nodes refer to the same
 * delegate, that delegate will be drawn only once. If [ForEachNode] is chosen, the delegate will be drawn for
 * every node that refers to it.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
enum class DelegationType {
    /** Will render the node only once per target delegate. */
    OncePerDelegate,
    /** Will render for each node independent of referring to the same delegate. */
    ForEachNode
}

/**
 * Node type that enables delegation of rendering. For rendering, not the node itself will be drawn,
 * but the node referred as [delegate]. A [delegationType] can be selected to choose whether the delegate
 * will be drawn for all nodes that refer to it, or only once.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
open class DelegatesRendering(val delegationType: DelegationType = DelegationType.OncePerDelegate, open var delegate: Node? = null): Node("DelegatesRendering")
