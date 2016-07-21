package scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock

/**
 * Class describing a [Node] of a [Scene], inherits from [Renderable]
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a node with default settings, e.g. identity matrices
 *  for model, view, projection, etc.
 * @property[name] The name of the [Node]
 */
open class Node(open var name: String) : Renderable {

    /** Hash map used for storing metadata for the Node. [DeferredLightingRenderer] uses
     * it to e.g. store [OpenGLObjectState]. */
    var metadata: HashMap<String, NodeMetadata> = HashMap()

    /** Material of the Node */
    override var material: Material? = null
    /** Initialisation flag. */
    override var initialized: Boolean = false
    /** Whether the Node is dirty and needs updating. */
    override var dirty: Boolean = true
    /** Flag to set whether the Node is visible or not. */
    override var visible: Boolean = true
    /** Is this Node an instance of another Node? */
    var instanceOf: Node? = null
    /** The Node's lock. */
    override var lock: ReentrantLock = ReentrantLock()

    /** bounding box **/
    var boundingBox: Box? = null
    /** bounding box coordinates **/
    var boundingBoxCoords: FloatArray? = null

    /**
     * Initialisation function for the Node.
     *
     * @return True of false whether initialisation was successful.
     */
    override fun init(): Boolean {
        return true
    }

    /** Name of the Node's type */
    open var nodeType = "Node"
    /** Should the Node's class name be used to derive a GLSL shader file name for a [GLProgram]? */
    open var useClassDerivedShader = false

    /** World transform matrix. Will create inverse [iworld] upon modification. */
    override var world: GLMatrix = GLMatrix.getIdentity()
        set(m) {
            this.iworld = m.getInverse()
            field = m
        }
    /** Inverse [world] transform matrix. */
    override var iworld: GLMatrix = GLMatrix.getIdentity()
    /** Model transform matrix. Will create inverse [imodel] upon modification. */
    override var model: GLMatrix = GLMatrix.getIdentity()
        set(m) {
            this.imodel = m.getInverse()
            field = m
        }
    /** Inverse [world] transform matrix. */
    override var imodel: GLMatrix = GLMatrix.getIdentity()

    /** View matrix. Will create inverse [iview] upon modification. */
    override var view: GLMatrix? = null
        set(m) {
            this.iview = m?.getInverse()
            field = m
        }
    /** Inverse [view] matrix. */
    override var iview: GLMatrix? = null

    /** Projection matrix. Will create inverse [iprojection] upon modification. */
    override var projection: GLMatrix? = null
        set(m) {
            this.iprojection = m?.getInverse()
            field = m
        }
    /** Inverse [projection] transform matrix. */
    override var iprojection: GLMatrix? = null

    /** ModelView matrix. Will create inverse [imodelview] upon modification. */
    override var modelView: GLMatrix? = null
        set(m) {
            this.imodelView = m?.getInverse()
            field = m
        }
    /** Inverse [modelView] transform matrix. */
    override var imodelView: GLMatrix? = null

    /** ModelViewProjection matrix. */
    override var mvp: GLMatrix? = null

    /** World position of the Node. Setting will trigger [world] update. */
    override var position: GLVector = GLVector(0.0f, 0.0f, 0.0f)
        set(v) {
            this.needsUpdate = true
            this.needsUpdateWorld = true
            field = v
        }

    /** x/y/z scale of the Node. Setting will trigger [world] update. */
    override var scale: GLVector = GLVector(1.0f, 1.0f, 1.0f)
        set(v) {
            this.needsUpdate = true
            this.needsUpdateWorld = true
            field = v
        }

    /** Rotation of the Node. Setting will trigger [world] update. */
    override var rotation: Quaternion = Quaternion(0.0f, 0.0f, 0.0f, 1.0f);
        set(q) {
            this.needsUpdate = true
            this.needsUpdateWorld = true
            field = q
        }

    /** Children of the Node. */
    var children: CopyOnWriteArrayList<Node>
    /** Other nodes that have linked transforms. */
    var linkedNodes: CopyOnWriteArrayList<Node>
    /** Parent node of this node. */
    var parent: Node? = null

    /** Flag to store whether the node is a billboard and will always face the camera. */
    override var isBillboard: Boolean = false

    /** Creation timestamp of the node. */
    var createdAt: Long = 0
    /** Modification timestamp of the node. */
    var modifiedAt: Long = 0

    /** Stores whether the [model] matrix needs an update. */
    var needsUpdate = true
    /** Stores whether the [world] matrix needs an update. */
    var needsUpdateWorld = true

    init {
        this.createdAt = (Timestamp(Date().time).time).toLong()
        this.model = GLMatrix.getIdentity()
        this.imodel = GLMatrix.getIdentity()

        this.modelView = GLMatrix.getIdentity()
        this.imodelView = GLMatrix.getIdentity()

        this.children = CopyOnWriteArrayList<Node>()
        this.linkedNodes = CopyOnWriteArrayList<Node>()
        // null should be the signal to use the default shader
    }

    /**
     * Attaches a child node to this node.
     *
     * @param[child] The child to attach to this node.
     */
    fun addChild(child: Node) {
        child.parent = this
        this.children.add(child)
    }

    /**
     * Removes a given node from the set of children of this node.
     *
     * @param[child] The child node to remove.
     */
    fun removeChild(child: Node): Boolean {
        return this.children.remove(child)
    }

    /**
     * Removes a given node from the set of children of this node.
     * If possible, use [removeChild] instead.
     *
     * @param[name] The name of the child node to remove.
     */
    fun removeChild(name: String): Boolean {
        for (c in this.children) {
            if (c.name.compareTo(name) == 0) {
                c.parent = null
                this.children.remove(c)
                return true
            }
        }

        return false
    }

    /**
     * Routine to call if the node has special requirements for drawing.
     */
    open fun draw() {

    }

    /**
     * Update the the [world] matrix of the [Node].
     *
     * This method will update the [model] and [world] matrices of the node,
     * if [needsUpdate] is true, or [force] is true. If [recursive] is true,
     * this method will also recurse into the [children] and [linkedNodes] of
     * the node and update these as well.
     *
     * @param[recursive] Whether the [children] should be recursed into.
     * @param[force] Force update irrespective of [needsUpdate] state.
     */
    fun updateWorld(recursive: Boolean, force: Boolean = false) {
        if (needsUpdate or force) {
            this.composeModel()

            needsUpdate = false
            needsUpdateWorld = true
        }

        if (needsUpdateWorld or force) {
            if (this.parent == null || this.parent is Scene) {
                this.world = this.model.clone()
                //          this.world.translate(this.position.x(), this.position.y(), this.position.z())
            } else {
                val m = parent!!.world.clone()
                m.mult(this.model)
                //m.translate(this.position.x(), this.position.y(), this.position.z())

                this.world = m
            }

            this.needsUpdateWorld = false
        }

        if (recursive) {
            this.children.forEach { it.updateWorld(true, true) }
            // also update linked nodes -- they might need updated
            // model/view/proj matrices as well
            this.linkedNodes.forEach { it.updateWorld(true, true) }
        }
    }

    /**
     * This method composes the [model] matrices of the node from its
     * [position], [scale] and [rotation].
     */
    fun composeModel() {
        val w = GLMatrix.getIdentity()
        //   w.translate(-this.position.x(), -this.position.y(), -this.position.z())
        w.mult(this.rotation)
        //    w.translate(this.position.x(), this.position.y(), this.position.z())
        w.scale(this.scale.x(), this.scale.y(), this.scale.z())
        w.translate(this.position.x(), this.position.y(), this.position.z())
        this.model = w
    }

    /**
     * This method composes the Node's [modelView] matrix.
     */
    fun composeModelView() {
        modelView = model.clone()
        modelView!!.mult(this.view ?: GLMatrix.getIdentity())
    }

    /**
     * This method composes the Node's [mvp] matrix. It runs
     * [composeModel] and [composeModelView] first.
     */
    fun composeMVP() {
        composeModel()
        composeModelView()

        mvp = modelView!!.clone()
        mvp!!.mult(projection)
    }


    fun generateBoundingBox() {

        if (this is Mesh) {
            if (vertices.size == 0) {
                System.err.println("Zero vertices currently, returning null bounding box")
                boundingBoxCoords = null
            } else {

                val x = vertices.filterIndexed { i, fl -> (i + 3).mod(3) == 0 }
                val y = vertices.filterIndexed { i, fl -> (i + 2).mod(3) == 0 }
                val z = vertices.filterIndexed { i, fl -> (i + 1).mod(3) == 0 }

                val xmin: Float = x.min()!!.toFloat()
                val xmax: Float = x.max()!!.toFloat()

                val ymin: Float = y.min()!!.toFloat()
                val ymax: Float = y.max()!!.toFloat()

                val zmin: Float = z.min()!!.toFloat()
                val zmax: Float = z.max()!!.toFloat()

                boundingBoxCoords = floatArrayOf(xmin, xmax, ymin, ymax, zmin, zmax)
                System.err.println("Created bouding box with ${boundingBoxCoords!!.joinToString(", ")}")
            }
        } else {
            System.err.println("Assuming 3rd party BB generation")
            // assume bounding box was created somehow
        }
    }

    companion object NodeHelpers {
        /**
         * Depth-first search for elements in a Scene.
         *
         * @param[s] The Scene to search in
         * @param[func] A lambda taking a [Node] and returning a Boolean for matching.
         * @return A list of [Node]s that match [func].
         */
        fun discover(origin: Node, func: (Node) -> Boolean): ArrayList<Node> {
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

            discover(origin, func)

            return matched
        }
    }
}
