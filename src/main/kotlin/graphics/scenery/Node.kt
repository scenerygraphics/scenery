package graphics.scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.LazyLogger
import kotlinx.coroutines.experimental.async
import java.io.Serializable
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
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
    @Transient final override var material: Material = Material.DefaultMaterial()
    /** Initialisation flag. */
    override var initialized: Boolean = false
    /** Whether the Node is dirty and needs updating. */
    override var dirty: Boolean = true
    /** Flag to set whether the Node is visible or not, recursively affects children. */
    override var visible: Boolean = true
        set(v) {
            children.forEach { it.visible = v }
            field = v
        }
    /** Is this Node an instance of another Node? */
    var instanceOf: Node? = null
    /** instanced properties */
    var instancedProperties = LinkedHashMap<String, () -> Any>()
    /** flag to set whether this node is an instance master */
    var instanceMaster: Boolean = false
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

    /**
     * Bounding sphere class, a bounding sphere is defined by an origin and a radius,
     * to enclose all of the Node's geometry.
     */
    data class BoundingSphere(val origin: GLVector, val radius: Float)

    /**
     * Oriented bounding box class to perform easy intersection tests.
     *
     * @property[min] The x/y/z minima for the bounding box.
     * @property[max] The x/y/z maxima for the bounding box.
     */
    inner class OrientedBoundingBox(val min: GLVector, val max: GLVector) {
        /**
         * Alternative [OrientedBoundingBox] constructor taking the [min] and [max] as a series of floats.
         */
        constructor(xMin: Float, yMin: Float, zMin: Float, xMax: Float, yMax: Float, zMax: Float) : this(GLVector(xMin, yMin, zMin), GLVector(xMax, yMax, zMax))

        /**
         * Alternative [OrientedBoundingBox] constructor, taking a 6-element float array for [min] and [max].
         */
        constructor(boundingBox: FloatArray) : this(GLVector(boundingBox[0], boundingBox[2], boundingBox[4]), GLVector(boundingBox[1], boundingBox[3], boundingBox[5]))

        /**
         * Returns the maximum bounding sphere of this bounding box.
         */
        fun getBoundingSphere(): BoundingSphere {
            val worldMin = worldPosition(min)
            val worldMax = worldPosition(max)

            val origin = (worldMin + worldMax) * 0.5f

            val radius = (worldMax - origin).magnitude()

            return BoundingSphere(origin, radius)
        }

        /**
         * Checks this [OrientedBoundingBox] for intersection with [other], and returns
         * true if the bounding boxes do intersect.
         */
        fun intersects(other: OrientedBoundingBox): Boolean {
            return other.getBoundingSphere().radius + getBoundingSphere().radius > (other.getBoundingSphere().origin - getBoundingSphere().origin).magnitude()
        }

        /**
         * Returns the hash code of this [OrientedBoundingBox], taking [min] and [max] into consideration.
         */
        override fun hashCode(): Int {
            return min.hashCode() + max.hashCode()
        }

        /**
         * Compares this bounding box to [other], returning true if they are equal.
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as? OrientedBoundingBox ?: return false

            if (min.hashCode() != other.min.hashCode()) return false
            if (max.hashCode() != other.max.hashCode()) return false

            return true
        }
    }

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

        this.getScene()?.sceneSize?.incrementAndGet()
        async {  this@Node.getScene()?.onChildrenAdded?.forEach { it.value.invoke(this@Node, child) } }

        if(child is PointLight) {
            this.getScene()?.lights?.add(child)
        }
    }

    /**
     * Removes a given node from the set of children of this node.
     *
     * @param[child] The child node to remove.
     */
    fun removeChild(child: Node): Boolean {
        this.getScene()?.sceneSize?.decrementAndGet()
        async { this@Node.getScene()?.onChildrenRemoved?.forEach { it.value.invoke(this@Node, child) } }

        if(child is PointLight) {
            this.getScene()?.lights?.remove(child)
        }

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

    internal open fun preUpdate(renderer: Renderer, hub: Hub) {

    }

    /**
     * PreDraw function, to be called before the actual rendering, useful for
     * per-timestep preparation.
     */
    open fun preDraw() {

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
            if (this.parent == null || this.parent is Scene) {
                world.copyFrom(model)
            } else {
                world.copyFrom(parent!!.world)
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
    fun composeModel() {
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

            if (vertexBufferView.capacity() == 0) {
                logger.warn("$name: Zero vertices currently, returning null bounding box")
                boundingBox = null
            } else {

                val vertex = floatArrayOf(0.0f, 0.0f, 0.0f)
                vertexBufferView.get(vertex)

                boundingBoxCoords[0] = vertex[0]
                boundingBoxCoords[1] = vertex[0]

                boundingBoxCoords[2] = vertex[1]
                boundingBoxCoords[3] = vertex[1]

                boundingBoxCoords[4] = vertex[2]
                boundingBoxCoords[5] = vertex[2]

                while(vertexBufferView.hasRemaining()) {
                    vertexBufferView.get(vertex)

                    boundingBoxCoords[0] = minOf(boundingBoxCoords[0], vertex[0])
                    boundingBoxCoords[2] = minOf(boundingBoxCoords[2], vertex[1])
                    boundingBoxCoords[4] = minOf(boundingBoxCoords[4], vertex[2])

                    boundingBoxCoords[1] = maxOf(boundingBoxCoords[1], vertex[0])
                    boundingBoxCoords[3] = maxOf(boundingBoxCoords[3], vertex[1])
                    boundingBoxCoords[5] = maxOf(boundingBoxCoords[5], vertex[2])
                }

                logger.debug("$name: Calculated bounding box with ${boundingBoxCoords.joinToString(", ")}")
                return OrientedBoundingBox(GLVector(boundingBoxCoords[0], boundingBoxCoords[2], boundingBoxCoords[4]),
                    GLVector(boundingBoxCoords[1], boundingBoxCoords[3], boundingBoxCoords[5]))
            }
        } else {
            logger.warn("$name: Assuming 3rd party BB generation")
            // assume bounding box was created somehow
            boundingBox = null
        }

        return null
    }

    private val shaderPropertyFieldCache = HashMap<String, KProperty1<Node, *>>()
    /**
     * Returns the [ShaderProperty] given by [name], if it exists and is declared by
     * this class or a subclass inheriting from [Node].
     */
    fun getShaderProperty(name: String): Any? {
        return if(shaderPropertyFieldCache.containsKey(name)) {
            val field = shaderPropertyFieldCache[name]!!
            val value = field.get(this)

            if(value !is HashMap<*, *>) {
                shaderPropertyFieldCache[name]!!.get(this)
            } else {
                value.get(name)
            }
        } else {
            val field = this.javaClass.kotlin.memberProperties.find { it.name == name && it.findAnnotation<ShaderProperty>() != null}

            if(field != null) {
                field.isAccessible = true

                shaderPropertyFieldCache.put(name, field)

                field.get(this)
            } else {
                this.javaClass.kotlin.memberProperties.filter { it.findAnnotation<ShaderProperty>() != null }.forEach { logger.info("${it.name} ${it.get(this)?.javaClass}")}
                val mappedProperties = this.javaClass.kotlin.memberProperties.firstOrNull { it.findAnnotation<ShaderProperty>() != null && it.get(this) is HashMap<*, *> && it.name == "shaderProperties" }

                if(mappedProperties == null) {
                    logger.warn("Could not find shader property '$name' in class properties or properties map!")
                    null
                } else {
                    mappedProperties.isAccessible = true

                    val map = mappedProperties.get(this) as? HashMap<String, Any>
                    if(map == null) {
                        null
                    } else {
                        shaderPropertyFieldCache.put(name, mappedProperties)
                        map.get(name)
                    }
                }
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

    private fun expand(lhs: OrientedBoundingBox, rhs: OrientedBoundingBox): OrientedBoundingBox {
        return OrientedBoundingBox(
            min(lhs.min.x(), rhs.min.x()),
            min(lhs.min.y(), rhs.min.y()),
            min(lhs.min.z(), rhs.min.z()),
            max(lhs.max.x(), rhs.max.x()),
            max(lhs.max.y(), rhs.max.y()),
            max(lhs.max.z(), rhs.max.z()))
    }

    /**
     * Returns the maximum [OrientedBoundingBox] of this [Node] and all its children.
     */
    fun getMaximumBoundingBox(): OrientedBoundingBox {
        if(boundingBox == null) {
            return OrientedBoundingBox(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        }

        if(children.none { it !is BoundingGrid }) {
            return OrientedBoundingBox(boundingBox?.min ?: GLVector(0.0f, 0.0f, 0.0f), boundingBox?.max ?: GLVector(0.0f, 0.0f, 0.0f))
        }

        return children
            .filter { it !is BoundingGrid  }.map { it.getMaximumBoundingBox() }
            .fold(boundingBox ?: OrientedBoundingBox(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f), { lhs, rhs -> expand(lhs, rhs) })
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
     * Code adapted from zachamarz, http://gamedev.stackexchange.com/a/18459
     */
    fun intersectAABB(origin: GLVector, dir: GLVector): Pair<Boolean, Float> {
        val bbmin = getMaximumBoundingBox().min.xyzw()
        val bbmax = getMaximumBoundingBox().max.xyzw()

        val min = world.mult(bbmin)
        val max = world.mult(bbmax)

        // skip if inside the bounding box
        if(origin.isInside(min, max)) {
            return false to 0.0f
        }

        val invDir = GLVector(1 / dir.x(), 1 / dir.y(), 1 / dir.z())

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
            return false to tmax
        }

        // we have missed the AABB
        if (tmin > tmax) {
            return false to tmax
        }

        // we have a match!
        return true to tmin
    }

    private fun GLVector.isInside(min: GLVector, max: GLVector): Boolean {
        return this.x() > min.x() && this.x() < max.x()
            && this.y() > min.y() && this.y() < max.y()
            && this.z() > min.z() && this.z() < max.z()
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
