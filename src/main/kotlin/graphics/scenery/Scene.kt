package graphics.scenery

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.channels.toList
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    fun findObserver(): Camera? {
        if(activeObserver == null) {
            val observers = discover(this, { n -> n.nodeType == "Camera" && (n as Camera?)?.active == true }, useDiscoveryBarriers = true)

            activeObserver = observers.firstOrNull() as Camera?
            return activeObserver
        } else {
            return activeObserver
        }
    }

    fun <T, R> List<T>.parallelFor (
        numThreads: Int = Runtime.getRuntime().availableProcessors() - 2,
        exec: ExecutorService = Executors.newFixedThreadPool(numThreads),
        transform: (T) -> R) {

        // default size is just an inlined version of kotlin.collections.collectionSizeOrDefault

        if(this.size < 4) {
            this.forEach { transform(it) }
        } else {
            for (item in this) {
                exec.submit { transform(item) }
            }
        }

//        exec.shutdown()
//        exec.awaitTermination(1, TimeUnit.DAYS)
    }

    fun <A, B>List<A>.pmap(f: suspend (A) -> B) = runBlocking {
        map { async(CommonPool) { f(it) } }.forEach { it.await() }
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
    fun findByClassname(className: String): List<Node> {
        return this.discover(this, { n -> n.javaClass.simpleName.contains(className) })
    }
}
