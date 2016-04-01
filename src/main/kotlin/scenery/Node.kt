@file:JvmName("Node")
package scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import java.sql.Timestamp
import java.util.*

open class Node(open var name: String) : Renderable {

    public var metadata: ArrayList<NodeMetadata> = ArrayList()

    override var material: Material? = null
    override var initialized: Boolean = false
    override var dirty: Boolean = true
    override var visible: Boolean = true

    override fun init(): Boolean {
        return true
    }

    open var nodeType = "Node"
    open var useClassDerivedShader = false

    override var world: GLMatrix = GLMatrix.getIdentity()
        set(m) {
            this.iworld = m.invert()
            field = m
        }
    override var iworld: GLMatrix = GLMatrix.getIdentity()
    override var model: GLMatrix = GLMatrix.getIdentity()
        set(m) {
            this.imodel = m.invert()
            field = m
        }
    override var imodel: GLMatrix = GLMatrix.getIdentity()

    override var view: GLMatrix? = null
        set(m) {
            this.iview = m?.invert()
            field = m
        }
    override var iview: GLMatrix? = null

    override var projection: GLMatrix? = null
        set(m) {
            this.iprojection = m?.invert()
            field = m
        }
    override var iprojection: GLMatrix? = null

    override var modelView: GLMatrix? = null
        set(m) {
            this.imodelView = m?.invert()
            field = m
        }
    override var imodelView: GLMatrix? = null
    override var mvp: GLMatrix? = null

    override var position: GLVector = GLVector(0.0f, 0.0f, 0.0f)
        set(v) {
            this.needsUpdate = true
            this.needsUpdateWorld = true
            field = v
        }

    override var scale: GLVector = GLVector(1.0f, 1.0f, 1.0f)
        set(v) {
            this.needsUpdate = true
            this.needsUpdateWorld = true
            field = v
        }
    override var rotation: Quaternion = Quaternion(0.0f, 0.0f, 0.0f, 1.0f);
        set(q) {
            this.needsUpdate = true
            this.needsUpdateWorld = true
            field = q
        }

    public var children: ArrayList<Node>
    public var linkedNodes: ArrayList<Node>
    public var parent: Node? = null

    // metadata
    var createdAt: Long = 0
    var modifiedAt: Long = 0

    public var needsUpdate = true
    public var needsUpdateWorld = true

    init {
        this.createdAt = (Timestamp(Date().time).time).toLong()
        this.model = GLMatrix.getIdentity()
        this.imodel = GLMatrix.getIdentity()

        this.modelView = GLMatrix.getIdentity()
        this.imodelView  = GLMatrix.getIdentity()

        this.children = ArrayList<Node>()
        this.linkedNodes = ArrayList<Node>()
        // null should be the signal to use the default shader
    }

    fun addChild(child: Node) {
        child.parent = this
        this.children.add(child)
    }

    fun removeChild(child: Node): Boolean {
        return this.children.remove(child)
    }

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

    open fun draw() {

    }

    fun updateWorld(recursive: Boolean, force: Boolean = false) {
        if (needsUpdate or force) {
            this.composeModel()
            needsUpdate = false
            needsUpdateWorld = true
        }

        if(needsUpdateWorld or force) {
            if (this.parent == null || this.parent is Scene) {
                this.world = this.model.clone()
            } else {
                val m = parent!!.world.clone()
                m.mult(this.model)

                this.world = m
            }

            this.needsUpdateWorld = false
        }

        if (recursive) {
            this.children.forEach { it.updateWorld(true) }
            // also update linked nodes -- they might need updated
            // model/view/proj matrices as well
            this.linkedNodes.forEach { it.updateWorld(true) }
        }
    }

    fun composeModel() {
        val w = GLMatrix.getIdentity()
        w.mult(this.rotation)
        w.scale(this.scale.x(), this.scale.y(), this.scale.z())
        w.translate(this.position!!.x(), this.position!!.y(), this.position!!.z())

        this.model = w
    }

    fun composeModelView() {
        modelView = model.clone()
        modelView!!.mult(this.view)
    }

    fun composeMVP() {
        composeModel()
        composeModelView()

        mvp = modelView!!.clone()
        mvp!!.mult(projection)
    }
}
