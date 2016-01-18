@file:JvmName("Node")
package scenery

import cleargl.GLMatrix
import cleargl.GLProgram
import cleargl.GLVector
import cleargl.RendererInterface
import com.jogamp.opengl.math.Quaternion
import java.sql.Timestamp
import java.util.*

open class Node(open var name: String) : Renderable {
    override var initialized: Boolean = false
    override var dirty: Boolean = true
    override var visible: Boolean = true

    override fun init(): Boolean {
        return true
    }

    override var renderer: RendererInterface? = null
    open var nodeType = "Node"

    override var program: GLProgram? = null
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

    override var position: GLVector? = null
    override var scale: GLVector? = null
    override var rotation: Quaternion = Quaternion(0.0f, 0.0f, 0.0f, 1.0f);

    public var children: ArrayList<Node>
    public var linkedNodes: ArrayList<Node>
    public var parent: Node? = null

    // metadata
    var createdAt: Long = 0
    var modifiedAt: Long = 0

    protected var needsUpdate = false

    init {
        this.createdAt = (Timestamp(Date().time).time).toLong()
        this.model = GLMatrix.getIdentity()
        this.imodel = GLMatrix.getIdentity()

        this.modelView = GLMatrix.getIdentity()
        this.imodelView  = GLMatrix.getIdentity()

        this.children = ArrayList<Node>()
        this.linkedNodes = ArrayList<Node>()
        // null should be the signal to use the default shader
        this.program = null
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

    fun setMVP(m: GLMatrix, v: GLMatrix, p: GLMatrix) {

    }

    fun updateWorld(recursive: Boolean) {
        if (needsUpdate) {
            if (this.parent == null) {
                this.composeModel()
            } else {
                val m = parent!!.model

                this.composeModel()
                m.mult(this.model)

                this.model = m
            }
        }

        if (recursive) {
            for (c in this.children) {
                c.updateWorld(true)
            }

            // also update linked nodes -- they might need updated
            // model/view/proj matrices as well
            for (c in this.linkedNodes) {
                c.updateWorld(true)
            }
        }
    }

    fun composeModel() {
        val w = GLMatrix.getIdentity()
        w.mult(GLMatrix.getScaling(this.scale))
        w.mult(GLMatrix.getTranslation(this.position))
        w.mult(this.rotation)

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
