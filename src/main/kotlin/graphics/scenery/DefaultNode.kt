package graphics.scenery

import graphics.scenery.attribute.DefaultAttributesMap
import graphics.scenery.net.Networkable
import graphics.scenery.utils.lazyLogger
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import kotlin.collections.HashMap
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

open class DefaultNode(name: String = "Node") : Node, Networkable {
    final override var name = name
        set(v) {
            field = v
            updateModifiedAt()
        }
    @Transient override var children = CopyOnWriteArrayList<Node>()
    @Transient override var linkedNodes = CopyOnWriteArrayList<Node>()
    @Transient override var metadata: HashMap<String, Any> = HashMap()
    override var parent: Node? = null
    override var modifiedAt = 0L
        get() {
            if (lastAttributesHashCode != attributes.hashCode()){
                field = System.nanoTime()
                lastAttributesHashCode = attributes.hashCode()
            }
            return field
        }
    override var discoveryBarrier = false
    @Transient override var update: ArrayList<() -> Unit> = ArrayList()
    @Transient override var postUpdate: ArrayList<() -> Unit> = ArrayList()
    override var visible: Boolean = true
        set(v) {
            children.forEach { it.visible = v }
            field = v
            updateModifiedAt()
        }
    @Transient override var initialized: Boolean = false
    override var state : State = State.Ready
    @Transient override var lock: ReentrantLock = ReentrantLock()
    override fun init(): Boolean {
        return true
    }

    override var boundingBox: OrientedBoundingBox? = null
    val logger by lazyLogger()

    @Transient private val attributes = DefaultAttributesMap()

    private var uuid: UUID = UUID.randomUUID()
    override fun getUuid(): UUID {
        return uuid
    }

    override fun getAttributes() = attributes

    override var networkID: Int = -1
    private var lastAttributesHashCode = attributes.hashCode()

    override fun addChild(child: Node) {
        child.parent = this
        this.children.add(child)

        val scene = this.getScene() ?: return
        scene.sceneSize.incrementAndGet()
        if(scene.onChildrenAdded.isNotEmpty()) {
            scene.onChildrenAdded.forEach { it.value.invoke(this@DefaultNode, child) }
        }
    }

    override fun removeChild(child: Node): Boolean {
        this.getScene()?.sceneSize?.decrementAndGet()
        this@DefaultNode.getScene()?.onChildrenRemoved?.forEach { it.value.invoke(this@DefaultNode, child) }

        return this.children.remove(child).let { if (it) {child.parent = null}; it }
    }

    override fun removeChild(name: String): Boolean {
        for (c in this.children) {
            if (c.name.compareTo(name) == 0) {
                return this.removeChild(c)
            }
        }
        return false
    }

    override fun getChildrenByName(name: String): List<Node> {
        return children.filter { it.name == name }
    }

    override fun getScene(): Scene? {
        var p: Node? = this
        while(p !is Scene && p != null) {
            p = p.parent
        }
        return p as? Scene
    }

    override fun getMaximumBoundingBox(): OrientedBoundingBox {
        if (boundingBox == null && children.size == 0) {
            return OrientedBoundingBox(this, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        }

        if (children.none { it !is BoundingGrid }) {
            return OrientedBoundingBox(
                this,
                boundingBox?.min ?: Vector3f(0.0f, 0.0f, 0.0f),
                boundingBox?.max ?: Vector3f(0.0f, 0.0f, 0.0f)
            )
        }

        return children
            .filter { it !is BoundingGrid }
            .map { it.getMaximumBoundingBox().translate(it.spatialOrNull()?.position ?: Vector3f(0f, 0f, 0f)) }
            .fold(
                boundingBox ?: OrientedBoundingBox(this, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
            ) { lhs, rhs -> lhs.expand(lhs, rhs) }
    }

    override fun runRecursive(func: (Node) -> Unit) {
        func.invoke(this)

        //use toArray to force a copy of children list
        children.toArray().forEach { (it as Node).runRecursive(func) }
    }

    override fun runRecursive(func: Consumer<Node>) {
        func.accept(this)

        //use toArray to force a copy of children list
        children.toArray().forEach { (it as Node).runRecursive(func) }
    }

    @Transient private val shaderPropertyFieldCache = HashMap<String, KProperty1<DefaultNode, *>>()
    override fun getShaderProperty(name: String): Any? {
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
                    logger.trace("ShaderProperty of ${this.name}: ${it.name} ${it.get(this)?.javaClass}")
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

            @Suppress("UNCHECKED_CAST")
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

    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
        if (fresh !is DefaultNode) throw IllegalArgumentException("Update called with object of foreign class")
        visible = fresh.visible
        name = fresh.name
    }

    override fun getSubcomponents(): List<Networkable> {
        return attributes.attributes().mapNotNull { it as? Networkable }
    }

    override fun toString(): String {
        return "$name(${javaClass.simpleName})"
    }
}
