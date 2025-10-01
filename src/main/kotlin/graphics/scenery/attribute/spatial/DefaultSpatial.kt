package graphics.scenery.attribute.spatial

import graphics.scenery.Camera
import graphics.scenery.DefaultNode
import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.net.Networkable
import graphics.scenery.utils.lazyLogger
import graphics.scenery.utils.MaybeIntersects
import graphics.scenery.utils.extensions.*
import net.imglib2.Localizable
import net.imglib2.RealLocalizable
import org.joml.*
import java.lang.Float.max
import java.lang.Float.min
import java.lang.Math
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

open class DefaultSpatial(@Transient protected var node: Node = DefaultNode()) : Spatial {
    override var world: Matrix4f = Matrix4f().identity()
        set(value) {
            propertyChanged(::world, field, value)
            field = value
        }
    override var model: Matrix4f = Matrix4f().identity()
        set(value) {
            propertyChanged(::model, field, value)
            field = value
        }
    override var view: Matrix4f = Matrix4f().identity()
        set(value) {
            propertyChanged(::view, field, value)
            field = value
        }
    override var projection: Matrix4f = Matrix4f().identity()
        set(value) {
            propertyChanged(::projection, field, value)
            field = value
        }
    override var scale: Vector3f = Vector3f(1.0f, 1.0f, 1.0f)
        set(value) {
            propertyChanged(::scale, field, value)
            field = value
        }
    override var rotation: Quaternionf = Quaternionf(0.0f, 0.0f, 0.0f, 1.0f)
        set(value) {
            propertyChanged(::rotation, field, value)
            field = value
        }
    override var position: Vector3f = Vector3f(0.0f, 0.0f, 0.0f)
        set(value) {
            propertyChanged(::position, field, value)
            field = value
        }

    /** Local X vector */
    override val localX: Vector3f
        get() {
            val v = Vector3f(1.0f, 0.0f, 0.0f)
            return world.transformDirection(v).normalize()
        }

    /** Local Y vector */
    override val localY: Vector3f
        get() {
            val v = Vector3f(0.0f, 1.0f, 0.0f)
            return world.transformDirection(v).normalize()
        }

    /** Local Z vector */
    override val localZ: Vector3f
        get() {
            val v = Vector3f(0.0f, 0.0f, 1.0f)
            return world.transformDirection(v).normalize()
        }

    override var wantsComposeModel = true
    @Transient override var needsUpdate = true
    @Transient override var needsUpdateWorld = true

    override var modifiedAt = 0L

    val logger by lazyLogger()

    @Suppress("UNUSED_PARAMETER")
    override fun <R> propertyChanged(property: KProperty<*>, old: R, new: R, custom: String) {
        if(property.name == "rotation"
            || property.name == "position"
            || property.name == "scale"
            || property.name == custom
        ) {
            needsUpdate = true
            needsUpdateWorld = true
        }
        updateModifiedAt()
    }

    @Synchronized
    override fun updateWorld(recursive: Boolean, force: Boolean) {
        node.update.forEach { it.invoke() }

        if ((needsUpdate or force)) {
            if (wantsComposeModel) {
                this.composeModel()
            }

            needsUpdate = false
            needsUpdateWorld = true
        }

        if (needsUpdateWorld or force) {
            val p = node.parent
            if (p == null || p is Scene) {
                world.set(model)
            } else {
                world.set(p.spatialOrNull()?.world)
                world.mul(this.model)
            }

            modifiedAt = System.nanoTime()
        }

        if (recursive) {
            node.children.forEach { it.spatialOrNull()?.updateWorld(true, needsUpdateWorld) }
            // also update linked nodes -- they might need updated
            // model/view/proj matrices as well
            node.linkedNodes.forEach { it.spatialOrNull()?.updateWorld(true, needsUpdateWorld) }
        }

        if (needsUpdateWorld) {
            needsUpdateWorld = false
        }

        node.postUpdate.forEach { it.invoke() }
    }

    override fun worldScale(): Vector3f {
        val wm = world
        val sx = Vector3f(wm[0, 0], wm[0, 1], wm[0, 2]).length()
        val sy = Vector3f(wm[1, 0], wm[1, 1], wm[1, 2]).length()
        val sz = Vector3f(wm[2, 0], wm[2, 1], wm[2, 2]).length()

        return Vector3f(sx, sy, sz)
    }

    override fun worldRotation(): Quaternionf{

        // unscale rotation part of matrix
        val scale = worldScale()
        val iScale = Vector3f(1/scale.x,1/scale.y,1/scale.z)
        val m = Matrix3f(world)
        m.scale(iScale)
        return Quaternionf().setFromNormalized(m)
    }

    /**
     * This method composes the [model] matrices of the node from its
     * [position], [scale] and [rotation].
     */
    override fun composeModel() {

        @Suppress("SENSELESS_COMPARISON")
        if(position != null && rotation != null && scale != null) {
            model.translationRotateScale(
                Vector3f(position.x(), position.y(), position.z()),
                this.rotation,
                Vector3f(this.scale.x(), this.scale.y(), this.scale.z())
            )
        }
    }

    override fun centerOn(position: Vector3f): Vector3f {
        val min = node.getMaximumBoundingBox().min
        val max = node.getMaximumBoundingBox().max

        val center = (max - min) * 0.5f
        this.position = position - (node.getMaximumBoundingBox().min + center)

        return center
    }

    /**
     *  This function rotates this spatial by a fixed [yaw] and [pitch] about the [target]
     *
     *  @param[yaw] yaw in degrees
     *  @param[pitch] pitch in degrees
     *  @param[target] the target position
     */
    override fun rotateAround(yaw: Float, pitch: Float, target: Vector3f) {
        val frameYaw = (yaw) / 180.0f * Math.PI.toFloat()
        val framePitch = pitch / 180.0f * Math.PI.toFloat()

        // first calculate the total rotation quaternion to be applied to the camera
        val yawQ = Quaternionf().rotateXYZ(0.0f, frameYaw, 0.0f).normalize()
        val pitchQ = Quaternionf().rotateXYZ(framePitch, 0.0f, 0.0f).normalize()

        node.ifSpatial {
            val distance = (target - position).length()
            rotation = pitchQ.mul(rotation).mul(yawQ).normalize()
            if(node is Camera) {
                position = target + (node as Camera).forward * distance * (-1.0f)
                (node as Camera).target = target
            } else {
                val forward = world.transform(Vector4f(1.0f, 0.0f, 0.0f, 1.0f)).xyz()
                position = target + forward * distance * (-1.0f)
            }
        }
    }


    override fun putAbove(position: Vector3f): Vector3f {
        val center = centerOn(position)

        val diffY = center.y() + position.y()
        val diff = Vector3f(0.0f, diffY, 0.0f)
        this.position = this.position + diff

        return diff
    }

    override fun fitInto(sideLength: Float, scaleUp: Boolean): Vector3f {
        val min = node.getMaximumBoundingBox().min.xyzw()
        val max = node.getMaximumBoundingBox().max.xyzw()

        val maxDimension = (max - min).max()
        val scaling = sideLength / maxDimension

        if((scaleUp && scaling > 1.0f) || scaling <= 1.0f) {
            this.scale = Vector3f(scaling, scaling, scaling)
        } else {
            this.scale = Vector3f(1.0f, 1.0f, 1.0f)
        }

        return this.scale
    }

    override fun orientBetweenPoints(p1: Vector3f, p2: Vector3f, rescale: Boolean, reposition: Boolean): Quaternionf {
        val direction = p2 - p1
        val length = direction.length()

        this.rotation = Quaternionf().rotationTo(Vector3f(0.0f, 1.0f, 0.0f), direction.normalize())
        if(rescale) {
            this.scale = Vector3f(1.0f, length, 1.0f)
        }

        if(reposition) {
            this.position = Vector3f(p1)
        }

        return this.rotation
    }


    /**
     * Checks whether this node's bounding box intersects the one of [other] using a simple bounding sphere test.
     * If [precise] is true, the intersection test will be performed using oriented bounding boxes (OBBs),
     * otherwise, a faster, but less precise bounding sphere test is performed.
     *
     * If any of the nodes to test do not have a bounding box defined, the result will be false.
     */
    override fun intersects(other: Node, precise: Boolean): Boolean {
        println("Testing intersection between:")
        println("* $node [box ${node.boundingBox}]")
        println("* $other [box ${node.boundingBox}]")
        node.boundingBox?.let { ownOBB ->
            other.boundingBox?.let { otherOBB ->
                return ownOBB.intersects(otherOBB, precise)
            }
        }

        return false
    }

    override fun worldPosition(v: Vector3f?): Vector3f =
        if(node.parent is Scene && v == null) {
            Vector3f(position)
        } else {
            world.transform(Vector4f().set(v ?: Vector3f(), 1.0f)).xyz()
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
    override fun intersectAABB(origin: Vector3f, dir: Vector3f, ignoreChildren: Boolean): MaybeIntersects {
        val bbmin = if (ignoreChildren) node.boundingBox!!.min.xyzw() else node.getMaximumBoundingBox().min.xyzw()
        val bbmax = if (ignoreChildren) node.boundingBox!!.max.xyzw() else node.getMaximumBoundingBox().max.xyzw()

        val min = world.transform(bbmin)
        val max = world.transform(bbmax)

        // skip if inside the bounding box
        if(origin.isInside(min.xyz(), max.xyz())) {
            return MaybeIntersects.NoIntersection()
        }

        val invDir =
            Vector3f(1 / (dir.x() + Float.MIN_VALUE), 1 / (dir.y() + Float.MIN_VALUE), 1 / (dir.z() + Float.MIN_VALUE))

        val t1 = (min.x() - origin.x()) * invDir.x()
        val t2 = (max.x() - origin.x()) * invDir.x()
        val t3 = (min.y() - origin.y()) * invDir.y()
        val t4 = (max.y() - origin.y()) * invDir.y()
        val t5 = (min.z() - origin.z()) * invDir.z()
        val t6 = (max.z() - origin.z()) * invDir.z()

        val tmin = max(max(min(t1, t2), min(t3, t4)), min(t5, t6))
        val tmax = min(min(max(t1, t2), max(t3, t4)), max(t5, t6))

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
        val localEntry = Matrix4f(world).invert().transform(Vector4f().set(entry, 1.0f))
        val localExit = Matrix4f(world).invert().transform(Vector4f().set(exit, 1.0f))

        return MaybeIntersects.Intersection(tmin, entry, exit, localEntry.xyz(), localExit.xyz())
    }

    private fun Vector3f.isInside(min: Vector3f, max: Vector3f): Boolean {
        return this.x() > min.x() && this.x() < max.x()
            && this.y() > min.y() && this.y() < max.y()
            && this.z() > min.z() && this.z() < max.z()
    }

    override fun localize(position: FloatArray?) {
        position?.set(0, this.position.x())
        position?.set(1, this.position.y())
        position?.set(2, this.position.z())
    }

    override fun localize(position: DoubleArray?) {
        position?.set(0, this.position.x().toDouble())
        position?.set(1, this.position.y().toDouble())
        position?.set(2, this.position.z().toDouble())
    }

    override fun getFloatPosition(d: Int): Float {
        return this.position[d]
    }

    override fun bck(d: Int) {
        move(-1, d)
    }

    override fun move(distance: Float, d: Int) {
        setPosition(getFloatPosition(d) + distance, d)
    }

    override fun move(distance: Double, d: Int) {
        setPosition(getDoublePosition(d) + distance, d)
    }

    override fun move(distance: RealLocalizable?) {
        distance?.getDoublePosition(0)?.let { move(it, 0) }
        distance?.getDoublePosition(1)?.let { move(it, 1) }
        distance?.getDoublePosition(2)?.let { move(it, 2) }
    }

    override fun move(distance: FloatArray?) {
        distance?.get(0)?.let { move(it, 0) }
        distance?.get(1)?.let { move(it, 1) }
        distance?.get(2)?.let { move(it, 2) }
    }

    override fun move(distance: DoubleArray?) {
        distance?.get(0)?.let { move(it, 0) }
        distance?.get(1)?.let { move(it, 1) }
        distance?.get(2)?.let { move(it, 2) }
    }

    override fun move(distance: Int, d: Int) {
        move(distance.toLong(), d)
    }

    override fun move(distance: Long, d: Int) {
        this.position = this.position + Vector3f().setComponent(d, distance.toFloat())
    }

    override fun move(distance: Localizable?) {
        distance?.getDoublePosition(0)?.let { move(it, 0) }
        distance?.getDoublePosition(1)?.let { move(it, 1) }
        distance?.getDoublePosition(2)?.let { move(it, 2) }
    }

    override fun move(distance: IntArray?) {
        distance?.get(0)?.let { move(it, 0) }
        distance?.get(1)?.let { move(it, 1) }
        distance?.get(2)?.let { move(it, 2) }
    }

    override fun move(distance: LongArray?) {
        distance?.get(0)?.let { move(it, 0) }
        distance?.get(1)?.let { move(it, 1) }
        distance?.get(2)?.let { move(it, 2) }
    }

    override fun numDimensions(): Int {
        return 3
    }

    override fun fwd(d: Int) {
        move(1, d)
    }

    override fun getDoublePosition(d: Int): Double {
        return this.position[d].toDouble()
    }

    override fun setPosition(pos: RealLocalizable) {
        position.setComponent(0, pos.getFloatPosition(0))
        position.setComponent(1, pos.getFloatPosition(1))
        position.setComponent(2, pos.getFloatPosition(2))
    }

    override fun setPosition(pos: FloatArray?) {
        pos?.get(0)?.let { setPosition(it, 0) }
        pos?.get(1)?.let { setPosition(it, 1) }
        pos?.get(2)?.let { setPosition(it, 2) }
    }

    override fun setPosition(pos: DoubleArray?) {
        pos?.get(0)?.let { setPosition(it, 0) }
        pos?.get(1)?.let { setPosition(it, 1) }
        pos?.get(2)?.let { setPosition(it, 2) }
    }

    override fun setPosition(pos: Float, d: Int) {
        position.setComponent(d, pos)
    }

    override fun setPosition(pos: Double, d: Int) {
        setPosition(pos.toFloat(), d)
    }

    override fun setPosition(pos: Localizable?) {
        pos?.getIntPosition(0)?.let { setPosition(it, 0) }
        pos?.getIntPosition(1)?.let { setPosition(it, 1) }
        pos?.getIntPosition(2)?.let { setPosition(it, 2) }
    }

    override fun setPosition(pos: IntArray?) {
        pos?.get(0)?.let { setPosition(it, 0) }
        pos?.get(1)?.let { setPosition(it, 1) }
        pos?.get(2)?.let { setPosition(it, 2) }
    }

    override fun setPosition(pos: LongArray?) {
        pos?.get(0)?.let { setPosition(it, 0) }
        pos?.get(1)?.let { setPosition(it, 1) }
        pos?.get(2)?.let { setPosition(it, 2) }
    }

    override fun setPosition(position: Int, d: Int) {
        setPosition(position.toLong(), d)
    }

    override fun setPosition(position: Long, d: Int) {
        setPosition(position.toFloat(), d)
    }

    override fun getAdditionalUpdateData(): Any? {
        return node.networkID
    }

    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
        if (fresh !is DefaultSpatial) {
            throw IllegalArgumentException("Got wrong type to update ${this::class.simpleName} ")
        }
        this.node = getNetworkable(additionalData as Int) as Node

        position = fresh.position
        rotation = fresh.rotation
        scale = fresh.scale
    }

    override fun getAttributeClass(): KClass<out Any> {
        return Spatial::class
    }

    override var networkID: Int = 0
}
