package graphics.scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.MaybeIntersects
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.Serializable
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Class describing a [Node] of a [Scene], inherits from [Renderable]
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a node with default settings, e.g. identity matrices
 *  for model, view, projection, etc.
 * @property[name] The name of the [Node]
 */
open class Node(open var name: String = "Node") : Renderable, Serializable {
    protected val logger by LazyLogger()

    /** Unique ID of the Node */
    var uuid: UUID = UUID.randomUUID()
        private set
    /** Hash map used for storing metadata for the Node. [Renderer] implementations use
     * it to e.g. store renderer-specific state. */
    @Transient var metadata: HashMap<String, Any> = HashMap()

    /** Material of the Node */
    final override var material: Material = Material.DefaultMaterial()
    /** Initialisation flag. */
    override var initialized: Boolean = false
    /** State of the Node **/
    override var state : State = State.Ready
    /** Whether the Node is dirty and needs updating. */
    override var dirty: Boolean = true
    /** Flag to set whether the Node is visible or not, recursively affects children. */
    override var visible: Boolean = true
        set(v) {
            children.forEach { it.visible = v }
            field = v
        }
    /** instanced properties */
    var instancedProperties = LinkedHashMap<String, () -> Any>()
    /** The Node's lock. */
    override var lock: ReentrantLock = ReentrantLock()

    /** bounding box **/
    var boundingBox: OrientedBoundingBox? = null

    /**
     * Initialisation function for the Node.
     *
     * @return True of false whether initialisation was successful.
     */
    override fun init(): Boolean {
        return true
    }

    /** Name of the Node's type */
    var nodeType = "Node"
        protected set

    /** Node update routine, called before updateWorld */
    var update: ArrayList<() -> Unit> = ArrayList()

    /** World transform matrix. Will create inverse [iworld] upon modification. */
    override var world: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Inverse [world] transform matrix. */
    override var iworld: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Model transform matrix. Will create inverse [imodel] upon modification. */
    override var model: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Inverse [world] transform matrix. */
    override var imodel: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }

    /** View matrix. Will create inverse [iview] upon modification. */
    override var view: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Inverse [view] matrix. */
    override var iview: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }

    /** Projection matrix. Will create inverse [iprojection] upon modification. */
    override var projection: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Inverse [projection] transform matrix. */
    override var iprojection: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }

    /** ModelView matrix. Will create inverse [imodelView] upon modification. */
    override var modelView: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Inverse [modelView] transform matrix. */
    override var imodelView: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }

    /** ModelViewProjection matrix. */
    override var mvp: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }

    /** World position of the Node. Setting will trigger [world] update. */
    override var position: GLVector by Delegates.observable(GLVector(0.0f, 0.0f, 0.0f)) { property, old, new -> propertyChanged(property, old, new) }

    /** x/y/z scale of the Node. Setting will trigger [world] update. */
    override var scale: GLVector by Delegates.observable(GLVector(1.0f, 1.0f, 1.0f)) { property, old, new -> propertyChanged(property, old, new) }

    /** Rendering scale, e.g. coming from physical units of the object. Setting will trigger [world] update. */
    @Deprecated("Do not use, see [scale] instead.")
    override var renderScale: Float by Delegates.observable(1.0f) { property, old, new -> propertyChanged(property, old, new) }

    /** Rotation of the Node. Setting will trigger [world] update. */
    override var rotation: Quaternion by Delegates.observable(Quaternion(0.0f, 0.0f, 0.0f, 1.0f)) { property, old, new -> propertyChanged(property, old, new) }

    /** Children of the Node. */
    @Transient var children: CopyOnWriteArrayList<Node>
    /** Other nodes that have linked transforms. */
    @Transient var linkedNodes: CopyOnWriteArrayList<Node>
    /** Parent node of this node. */
    var parent: Node? = null

    /** Flag to store whether the node is a billboard and will always face the camera. */
    override var isBillboard: Boolean = false

    /** Creation timestamp of the node. */
    var createdAt: Long = 0
    /** Modification timestamp of the node. */
    var modifiedAt: Long = 0

    /** Stores whether the [model] matrix needs an update. */
    var wantsComposeModel = true
    /** Stores whether the [model] matrix needs an update. */
    var needsUpdate = true
    /** Stores whether the [world] matrix needs an update. */
    var needsUpdateWorld = true

    var discoveryBarrier = false

    val instances = CopyOnWriteArrayList<Node>()

    @Suppress("UNUSED_PARAMETER")
    protected fun <R> propertyChanged(property: KProperty<*>, old: R, new: R) {
        if(property.name == "rotation"
            || property.name == "position"
            || property.name  == "scale"
            || property.name == "renderScale") {
            needsUpdate = true
            needsUpdateWorld = true
        }
    }

    init {
        createdAt = (Timestamp(Date().time).time)

        children = CopyOnWriteArrayList()
        linkedNodes = CopyOnWriteArrayList()
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

        val scene = this.getScene() ?: return
        scene.sceneSize.incrementAndGet()
        if(scene.onChildrenAdded.isNotEmpty()) {
            GlobalScope.launch {
                scene.onChildrenAdded.forEach { it.value.invoke(this@Node, child) }
            }
        }
    }

    /**
     * Removes a given node from the set of children of this node.
     *
     * @param[child] The child node to remove.
     */
    fun removeChild(child: Node): Boolean {
        this.getScene()?.sceneSize?.decrementAndGet()
        GlobalScope.launch { this@Node.getScene()?.onChildrenRemoved?.forEach { it.value.invoke(this@Node, child) } }

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
     * Returns all children with the given [name].
     */
    fun getChildrenByName(name: String): List<Node> {
        return children.filter { it.name == name }
    }

    /**
     * Routine to call if the node has special requirements for drawing.
     */
    open fun draw() {

    }

    internal open fun preUpdate(renderer: Renderer, hub: Hub?) {

    }

    /**
     * PreDraw function, to be called before the actual rendering, useful for
     * per-timestep preparation.
     */
    open fun preDraw(): Boolean {
        return true
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
    @Synchronized fun updateWorld(recursive: Boolean, force: Boolean = false) {
        update.forEach { it.invoke() }

        if ((needsUpdate or force) && wantsComposeModel) {
            this.composeModel()

            needsUpdate = false
            needsUpdateWorld = true
        }

        if (needsUpdateWorld or force) {
            val p = parent
            if (p == null || p is Scene) {
                world.copyFrom(model)
            } else {
                world.copyFrom(p.world)
                world.mult(this.model)
            }
        }

        if (recursive) {
            this.children.forEach { it.updateWorld(true, needsUpdateWorld) }
            // also update linked nodes -- they might need updated
            // model/view/proj matrices as well
            this.linkedNodes.forEach { it.updateWorld(true, needsUpdateWorld) }
        }

        if(needsUpdateWorld) {
            needsUpdateWorld = false
        }
    }

    /**
     * This method composes the [model] matrices of the node from its
     * [position], [scale] and [rotation].
     */
    open fun composeModel() {
        @Suppress("SENSELESS_COMPARISON")
        if(position != null && rotation != null && scale != null) {
            model.setIdentity()
            model.translate(this.position.x(), this.position.y(), this.position.z())
            model.mult(this.rotation)
            model.scale(this.renderScale, this.renderScale, this. renderScale)
            model.scale(this.scale.x(), this.scale.y(), this.scale.z())
        }
    }

    /**
     * Generates an [OrientedBoundingBox] for this [Node]. This will take
     * geometry information into consideration if this Node implements [HasGeometry].
     * In case a bounding box cannot be determined, the function will return null.
     */
    open fun generateBoundingBox(): OrientedBoundingBox? {
        if (this is HasGeometry) {
            val vertexBufferView = vertices.asReadOnlyBuffer()
            val boundingBoxCoords = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)

            if (vertexBufferView.capacity() == 0 || vertexBufferView.remaining() == 0) {
                boundingBox = if(!children.none()) {
                    getMaximumBoundingBox()
                } else {
                    logger.warn("$name: Zero vertices currently, returning empty bounding box")
                    OrientedBoundingBox(this,0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f)
                }

                return boundingBox
            } else {

                val vertex = floatArrayOf(0.0f, 0.0f, 0.0f)
                vertexBufferView.get(vertex)

                boundingBoxCoords[0] = vertex[0]
                boundingBoxCoords[1] = vertex[0]

                boundingBoxCoords[2] = vertex[1]
                boundingBoxCoords[3] = vertex[1]

                boundingBoxCoords[4] = vertex[2]
                boundingBoxCoords[5] = vertex[2]

                while(vertexBufferView.remaining() >= 3) {
                    vertexBufferView.get(vertex)

                    boundingBoxCoords[0] = minOf(boundingBoxCoords[0], vertex[0])
                    boundingBoxCoords[2] = minOf(boundingBoxCoords[2], vertex[1])
                    boundingBoxCoords[4] = minOf(boundingBoxCoords[4], vertex[2])

                    boundingBoxCoords[1] = maxOf(boundingBoxCoords[1], vertex[0])
                    boundingBoxCoords[3] = maxOf(boundingBoxCoords[3], vertex[1])
                    boundingBoxCoords[5] = maxOf(boundingBoxCoords[5], vertex[2])
                }

                logger.debug("$name: Calculated bounding box with ${boundingBoxCoords.joinToString(", ")}")
                return OrientedBoundingBox(this, GLVector(boundingBoxCoords[0], boundingBoxCoords[2], boundingBoxCoords[4]),
                    GLVector(boundingBoxCoords[1], boundingBoxCoords[3], boundingBoxCoords[5]))
            }
        } else {
            logger.warn("$name: Assuming 3rd party BB generation")
            return boundingBox
        }
    }

    @Transient private val shaderPropertyFieldCache = HashMap<String, KProperty1<Node, *>>()
    /**
     * Returns the [ShaderProperty] given by [name], if it exists and is declared by
     * this class or a subclass inheriting from [Node]. Returns null if the [name] can
     * neither be found as a property, or as member of the shaderProperties HashMap the Node
     * might declare.
     */
    fun getShaderProperty(name: String): Any? {
        // first, try to find the shader property in the cache, and either return it,
        // or, if the member of the cache is the shaderProperties HashMap, return the member of it.
        val f = shaderPropertyFieldCache[name]
        if (f != null) {
            val value = f.get(this)

            return if (value !is HashMap<*, *>) {
                f.get(this)
            } else {
                value.get(name)
            }
        }

        // First fallthrough: In case the field is not in the cache, check all member properties
        // containing the [ShaderProperty] annotation. If the property is found,
        // cache it for performance reasons and return it.
        val field = this.javaClass.kotlin.memberProperties.find { it.name == name && it.findAnnotation<ShaderProperty>() != null }

        if (field != null) {
            field.isAccessible = true

            shaderPropertyFieldCache.put(name, field)

            return field.get(this)
        }

        // Last fallthrough: If [name] cannot be found as a property, try to locate it in the
        // shaderProperties HashMap and return it. If it cannot be found here either, return null.
        this.javaClass.kotlin.memberProperties
            .filter { it.findAnnotation<ShaderProperty>() != null }
            .forEach {
                it.isAccessible = true
                if(logger.isTraceEnabled) {
                    logger.trace("ShaderProperty of ${this@Node.name}: ${it.name} ${it.get(this)?.javaClass}")
                }
            }
        val mappedProperties = this.javaClass.kotlin.memberProperties
            .firstOrNull {
                it.findAnnotation<ShaderProperty>() != null && it.get(this) is HashMap<*, *> && it.name == "shaderProperties"
            }

        return if (mappedProperties == null) {
            logger.warn("Could not find shader property '$name' in class properties or properties map!")
            null
        } else {
            mappedProperties.isAccessible = true

            val map = mappedProperties.get(this) as? HashMap<String, Any>
            if (map == null) {
                logger.warn("$this: $name not found in shaderProperties hash map")
                null
            } else {
                shaderPropertyFieldCache.put(name, mappedProperties)
                map.get(name)
            }
        }
    }

    /**
     * Returns the [Scene] this Node is ultimately attached to.
     * Will return null in case the Node is not attached to a [Scene] yet.
     */
    fun getScene(): Scene? {
        var p: Node? = this
        while(p !is Scene && p != null) {
            p = p.parent
        }

        return p as? Scene
    }

    /**
     * Centers the [Node] on a given position.
     *
     * @param[position] - the position to center the [Node] on.
     * @return GLVector - the center offset calculcated for the [Node].
     */
    fun centerOn(position: GLVector): GLVector {
        val min = getMaximumBoundingBox().min.xyzw()
        val max = getMaximumBoundingBox().max.xyzw()

        val center = (max - min) * 0.5f
        this.position = position - (getMaximumBoundingBox().min + center)

        return center
    }

    /**
     * Taking this [Node]'s [boundingBox] into consideration, puts it above
     * the [position] entirely.
     */
    fun putAbove(position: GLVector): GLVector {
        val center = centerOn(position)

        val diffY = center.y() + position.y()
        val diff = GLVector(0.0f, diffY, 0.0f)
        this.position = this.position + diff

        return diff
    }

    /**
     * Fits the [Node] within a box of the given dimension.
     *
     * @param[sideLength] - The size of the box to fit the [Node] uniformly into.
     * @param[scaleUp] - Whether the model should only be scaled down, or also up.
     * @return GLVector - containing the applied scaling
     */
    fun fitInto(sideLength: Float, scaleUp: Boolean = false): GLVector {
        val min = getMaximumBoundingBox().min.xyzw() ?: return GLVector.getNullVector(3)
        val max = getMaximumBoundingBox().max.xyzw() ?: return GLVector.getNullVector(3)

        (max - min).toFloatArray().max()?.let { maxDimension ->
            val scaling = sideLength/maxDimension

            if((scaleUp && scaling > 1.0f) || scaling <= 1.0f) {
                this.scale = GLVector(scaling, scaling, scaling)
            } else {
                this.scale = GLVector(1.0f, 1.0f, 1.0f)
            }
        }

        return this.scale
    }

    /**
     * Orients the Node between points [p1] and [p2], and optionally
     * [rescale]s and [reposition]s it.
     */
    @JvmOverloads fun orientBetweenPoints(p1: GLVector, p2: GLVector, rescale: Boolean = false, reposition: Boolean = false): Quaternion {
        val direction = p2 - p1
        this.rotation = this.rotation
            .setLookAt(direction.normalized.toFloatArray(),
                floatArrayOf(0.0f, 1.0f, 0.0f),
                FloatArray(3), FloatArray(3), FloatArray(3))
            .rotateByAngleX(PI.toFloat()/2.0f)
        if(rescale) {
            this.scale = GLVector(1.0f, direction.magnitude(), 1.0f)
        }

        if(reposition) {
            this.position = p1.clone()
        }

        return this.rotation
    }

    /**
     * Returns the maximum [OrientedBoundingBox] of this [Node] and all its children.
     */
    fun getMaximumBoundingBox(): OrientedBoundingBox {
        if(boundingBox == null && children.size == 0) {
            return OrientedBoundingBox(this,0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        }

        if(children.none { it !is BoundingGrid }) {
            return OrientedBoundingBox(this,boundingBox?.min ?: GLVector(0.0f, 0.0f, 0.0f), boundingBox?.max ?: GLVector(0.0f, 0.0f, 0.0f))
        }

        return children
            .filter { it !is BoundingGrid  }.map { it.getMaximumBoundingBox() }
            .fold(boundingBox ?: OrientedBoundingBox(this, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f), { lhs, rhs -> lhs.expand(lhs, rhs) })
    }

    /**
     * Checks whether two node's bounding boxes do intersect using a simple bounding sphere test.
     */
    fun intersects(other: Node): Boolean {
        boundingBox?.let { ownOBB ->
            other.boundingBox?.let { otherOBB ->
                return ownOBB.intersects(otherOBB)
            }
        }

        return false
    }

    /**
     * Returns the [Node]'s world position
     *
     * @returns The position in world space
     */
    fun worldPosition(v: GLVector? = null): GLVector {
        val target = v ?: position
        return if(parent is Scene && v == null) {
            target.clone()
        } else {
            world.mult(GLVector(target.x(), target.y(), target.z(), 1.0f)).xyz()
        }
    }

    /**
     *  Runs an operation recursively on the node itself and all child nodes.
     *
     *  @param[func] A lambda accepting a [Node], representing this node and its potential children.
     */
    fun runRecursive(func: (Node) -> Unit) {
        func.invoke(this)

        children.forEach { it.runRecursive(func) }
    }

    /**
     *  Runs an operation recursively on the node itself and all child nodes.
     *
     *  @param[func] A Java [Consumer] accepting a [Node], representing this node and its potential children.
     */
    fun runRecursive(func: Consumer<Node>) {
        func.accept(this)

        children.forEach { it.runRecursive(func) }
    }

    /**
     * Performs a intersection test with an axis-aligned bounding box of this [Node], where
     * the test ray originates at [origin] and points into [dir].
     *
     * Returns a Pair of Boolean and Float, indicating whether an intersection is possible,
     * and at which distance.
     *
     * Code adapted from [zachamarz](http://gamedev.stackexchange.com/a/18459).
     */
    fun intersectAABB(origin: GLVector, dir: GLVector): MaybeIntersects {
        val bbmin = getMaximumBoundingBox().min.xyzw()
        val bbmax = getMaximumBoundingBox().max.xyzw()

        val min = world.mult(bbmin)
        val max = world.mult(bbmax)

        // skip if inside the bounding box
        if(origin.isInside(min, max)) {
            return MaybeIntersects.NoIntersection()
        }

        val invDir = GLVector(1 / (dir.x() + Float.MIN_VALUE), 1 / (dir.y() + Float.MIN_VALUE), 1 / (dir.z() + Float.MIN_VALUE))

        val t1 = (min.x() - origin.x()) * invDir.x()
        val t2 = (max.x() - origin.x()) * invDir.x()
        val t3 = (min.y() - origin.y()) * invDir.y()
        val t4 = (max.y() - origin.y()) * invDir.y()
        val t5 = (min.z() - origin.z()) * invDir.z()
        val t6 = (max.z() - origin.z()) * invDir.z()

        val tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6))
        val tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6))

        // we are in front of the AABB
        if (tmax < 0) {
            return MaybeIntersects.NoIntersection()
        }

        // we have missed the AABB
        if (tmin > tmax) {
            return MaybeIntersects.NoIntersection()
        }

        // we have a match! calculate entry and exit points
        val entry = origin + dir * tmin
        val exit = origin + dir * tmax
        val localEntry = world.inverse.mult(entry.xyzw())
        val localExit = world.inverse.mult(exit.xyzw())

        return MaybeIntersects.Intersection(tmin, entry, exit, localEntry.xyz(), localExit.xyz())
    }

    private fun GLVector.isInside(min: GLVector, max: GLVector): Boolean {
        return this.x() > min.x() && this.x() < max.x()
            && this.y() > min.y() && this.y() < max.y()
            && this.z() > min.z() && this.z() < max.z()
    }

    override fun toString(): String {
        return "$name(${javaClass?.simpleName})"
    }

    companion object NodeHelpers {
        /**
         * Depth-first search for elements in a Scene.
         *
         * @param[origin] The [Node] to start the search at.
         * @param[func] A lambda taking a [Node] and returning a Boolean for matching.
         * @return A list of [Node]s that match [func].
         */
        @Suppress("unused")
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
