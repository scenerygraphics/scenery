package scenery

import java.util.*

/**
 * Scene class. A Scene is a special kind of [Node] that can only exist once per graph,
 * as a root node.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Scene : Node("RootNode") {

    /** Temporary storage of the active observer ([Camera]) of the Scene. */
    var activeObserver: Camera? = null

    /**
     * Adds a [Node] to the Scene, at the position given by [parent]
     *
     * @param[n] The node to add.
     * @param[parent] The node to attach [n] to.
     */
    fun addNode(n: Node, parent: Node) {
        if (n.name == "RootNode") {
            throw IllegalStateException("Only one RootNode may exist per scenegraph. Please choose a different name.")
        }

        discover(this, { node -> node == parent }).first().addChild(n)
    }

    /**
     * Find the currently active observer in the Scene.
     *
     * TODO: Store once-found camera in [activeObserver]
     *
     * @return The [Camera] that is currently active.
     */
    fun findObserver(): Camera {
        if(activeObserver == null) {
            var observers = discover(this, { n -> n.nodeType == "Camera" && (n as Camera?)?.active == true })

            activeObserver = observers.first() as Camera
            return activeObserver!!
        } else {
            return activeObserver!!
        }
    }

    /**
     * Depth-first search for elements in a Scene.
     *
     * @param[s] The Scene to search in
     * @param[func] A lambda taking a [Node] and returning a Boolean for matching.
     * @return A list of [Node]s that match [func].
     */
    fun discover(s: Scene, func: (Node) -> Boolean): ArrayList<Node> {
        val visited = HashSet<Node>()
        val matched = ArrayList<Node>()

        fun discover(current: Node, f: (Node) -> Boolean) {
            if (!visited.add(current)) return
            for (v in current.children) {
                if (f(v)) {
                    matched.add(v)
                }
                discover(v, f)
            }
        }

        discover(s as Node, func)

        return matched
    }

    /**
     * Depth-first search routine for a Scene.
     */
    fun dfs(s: Scene) {
        val visited = HashSet<Node>()
        fun dfs(current: Node) {
            if (!visited.add(current)) return
            for (v in current.children)
                dfs(v)
        }

        dfs(s.children[0])
    }

    /**
     * Find a [Node] by its name.
     *
     * @param[name] The name of the [Node] to find.
     * @return The first [Node] matching [name].
     */
    fun find(name: String): Node {
        var matches = discover(this, { n -> n.name == name })

        return matches.first()
    }

    /**
     * Find a [Node] by its class name.
     *
     * @param[name] The class name of the [Node] to find.
     * @return A list of Nodes with class name [name].
     */
    fun findByClassname(className: String): ArrayList<Node> {
        return this.discover(this, { n -> n.javaClass.simpleName.contains(className) })
    }
}
