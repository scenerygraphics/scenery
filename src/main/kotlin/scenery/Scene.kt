package scenery

import java.util.*

open class Scene : Node("RootNode") {

    var activeObserver: Camera? = null

    fun addNode(n: Node, parent: Node) {
        if (n.name == "RootNode") {
            throw IllegalStateException("Only one RootNode may exist per scenegraph. Please choose a different name.")
        }

        discover(this, { node -> node == parent }).first().addChild(n)
    }

    fun findObserver() : Camera {
        var observers = discover(this, { n -> n.nodeType == "Camera" && (n as Camera?)?.active == true})

        return observers.first() as Camera
    }

    fun discover(s: Scene, func: (Node) -> Boolean) : ArrayList<Node> {
        val visited = HashSet<Node>()
        val matched = ArrayList<Node>()

        fun discover(current: Node, f: (Node) -> Boolean) {
            if (!visited.add(current)) return
            for (v in current.children) {
                if(f(v)) {
                    matched.add(v)
                }
                discover(v, f)
            }
        }

        discover(s as Node, func)

        return matched
    }

    fun dfs(s: Scene) {
        val visited = HashSet<Node>()
        fun dfs(current: Node) {
            if (!visited.add(current)) return
            for (v in current.children)
                dfs(v)
        }

        dfs(s.children[0])
    }

    fun find(name: String): Node {
        var matches = discover(this, { n -> n.name == name})

        return matches.first()
    }

    fun findByClassname(className: String): ArrayList<Node> {
        return this.discover(this, { n -> n.javaClass.simpleName.contains(className) })
    }
}
