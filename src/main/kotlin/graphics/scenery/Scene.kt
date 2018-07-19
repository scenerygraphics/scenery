package graphics.scenery

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.ArrayList

/**
 * Scene class. A Scene is a special kind of [Node] that can only exist once per graph,
 * as a root node.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Scene : Node("RootNode") {

    /** Temporary storage of the active observer ([Camera]) of the Scene. */
    var activeObserver: Camera? = null

    internal var sceneSize: AtomicLong = AtomicLong(0L)

    /** Temporary storage for lights */
    var lights = ArrayList<Node>()

    /** Callbacks to be called when a child is added to the scene */
    var onChildrenAdded = ConcurrentHashMap<String, (Node, Node) -> Unit>()
    /** Callbacks to be called when a child is removed from the scene */
    var onChildrenRemoved = ConcurrentHashMap<String, (Node, Node) -> Unit>()
    /** Callbacks to be called when a child is removed from the scene */
    var onNodePropertiesChanged = ConcurrentHashMap<String, (Node) -> Unit>()

    /**
     * Adds a [Node] to the Scene, at the position given by [parent]
     *
     * @param[n] The node to add.
     * @param[parent] The node to attach [n] to.
     */
    @Suppress("unused")
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
    fun findObserver(): Camera? {
        return if(activeObserver == null) {
            val observers = discover(this, { n -> n.nodeType == "Camera" && (n as Camera?)?.active == true }, useDiscoveryBarriers = true)

            activeObserver = observers.firstOrNull() as Camera?
            activeObserver
        } else {
            activeObserver
        }
    }

    /**
     * Depth-first search for elements in a Scene.
     *
     * @param[s] The Scene to search in
     * @param[func] A lambda taking a [Node] and returning a Boolean for matching.
     * @return A list of [Node]s that match [func].
     */
    fun discover(s: Scene, func: (Node) -> Boolean, useDiscoveryBarriers: Boolean = false): ArrayList<Node> {
        val visited = HashSet<Node>()
        val matched = ArrayList<Node>()

        fun discover(current: Node, f: (Node) -> Boolean) {
            if (!visited.add(current)) return
            for (v in current.children) {
                if (f(v)) {
                    matched.add(v)
                }

                if(!(useDiscoveryBarriers && v.discoveryBarrier)) {
                    discover(v, f)
                }
            }
        }

        discover(s as Node, func)

        return matched
    }

    /**
     * Discovers [Node]s in a Scene [s] via [func] in a parallel manner, optionally stopping at discovery barriers,
     * if [useDiscoveryBarriers] is true.
     */
    @Suppress("UNUSED_VARIABLE", "unused")
    fun discoverParallel(s: Scene, func: (Node) -> Boolean, useDiscoveryBarriers: Boolean = false) = runBlocking<List<Node>> {
        val visited = Collections.synchronizedSet(HashSet<Node>(sceneSize.toInt()))
        val crs = Collections.synchronizedSet(HashSet<Job>())
        val matches = Collections.synchronizedSet(HashSet<Node>(sceneSize.toInt()))

        val channel = Channel<Node>()

        fun discover(current: Node, f: (Node) -> Boolean) {
            if (!visited.add(current)) return

            crs.add(launch {
                for(v in current.children) {
                    if (f(v)) {
//                        channel.send(v)
                        matches.add(v)
                    }

                    if (useDiscoveryBarriers && v.discoveryBarrier) {
                        logger.trace("Hit discovery barrier, not recursing deeper.")
                    } else {
                        discover(v, f)
                    }
                }
            })
        }

        discover(s as Node, func)

//        channel.close()

        crs.forEach { it.join() }
//        channel.consumeEach { logger.info("Added ${it.name}"); matches.add(it) }
        matches.toList()
    }

    /**
     * Depth-first search routine for a Scene.
     */
    @Suppress("unused")
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
    @Suppress("unused")
    fun find(name: String): Node? {
        val matches = discover(this, { n -> n.name == name })

        return matches.firstOrNull()
    }

    /**
     * Find a [Node] by its class name.
     *
     * @param[className] The class name of the [Node] to find.
     * @return A list of Nodes with class name [name].
     */
    @Suppress("unused")
    fun findByClassname(className: String): List<Node> {
        return this.discover(this, { n -> n.javaClass.simpleName.contains(className) })
    }
}
