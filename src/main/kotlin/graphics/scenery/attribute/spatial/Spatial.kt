package graphics.scenery.attribute.spatial

import graphics.scenery.Node
import graphics.scenery.net.Networkable
import graphics.scenery.utils.MaybeIntersects
import net.imglib2.RealLocalizable
import net.imglib2.RealPositionable
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.reflect.KProperty

interface Spatial: RealLocalizable, RealPositionable, Networkable {
    /** Model matrix **/
    var model: Matrix4f
    /** World transform matrix */
    var world: Matrix4f
    /** View matrix. May be null. */
    var view: Matrix4f
    /** Projection matrix. May be null. */
    var projection: Matrix4f
    /** World position of the [Renderable] object. */
    var position: Vector3f
    /** X/Y/Z scale of the object. */
    var scale: Vector3f
    /** Quaternion defining the rotation of the object in local coordinates. */
    var rotation: Quaternionf
    /** Stores whether the [model] matrix needs an update. */
    var wantsComposeModel: Boolean
    /** Stores whether the [model] matrix needs an update. */
    var needsUpdate: Boolean
    /** Stores whether the [world] matrix needs an update. */
    var needsUpdateWorld: Boolean

    /** Local X vector */
    val localX: Vector3f
    /** Local Y vector */
    val localY: Vector3f
    /** Local Z vector */
    val localZ: Vector3f

    /**
     * Update the the [world] matrix of the [Spatial] node.
     *
     * This method will update the [model] and [world] matrices of the node,
     * if [needsUpdate] is true, or [force] is true. If [recursive] is true,
     * this method will also recurse into the [children] and [linkedNodes] of
     * the node and update these as well.
     *
     * @param[recursive] Whether the [children] should be recursed into.
     * @param[force] Force update irrespective of [needsUpdate] state.
     */
    fun updateWorld(recursive: Boolean, force: Boolean = false)

    /**
     * Extracts the scaling component from the world matrix.
     *
     * Is not correct for world matrices with shear!
     *
     * @return world scale
     */
    fun worldScale(): Vector3f

    /**
     * Extracts the rotation component from the world matrix
     *
     * Is not correct for world matrices with shear or are anisotropic!
     */
    fun worldRotation(): Quaternionf

    fun intersectAABB(origin: Vector3f, dir: Vector3f): MaybeIntersects

    /**
     * Returns the [Node]'s world position
     *
     * @returns The position in world space
     */
    fun worldPosition(v: Vector3f? = null): Vector3f

    /**
     * Checks whether this node's bounding box intersects the one of [other] using a simple bounding sphere test.
     * If [precise] is true, the intersection test will be performed using oriented bounding boxes (OBBs),
     * otherwise, a faster, but less precise bounding sphere test is performed.
     */
    fun intersects(other: Node, precise: Boolean = false): Boolean

    /**
     * Fits the [Node] within a box of the given dimension.
     *
     * @param[sideLength] - The size of the box to fit the [Node] uniformly into.
     * @param[scaleUp] - Whether the model should only be scaled down, or also up.
     * @return Vector3f - containing the applied scaling
     */
    fun fitInto(sideLength: Float, scaleUp: Boolean = false): Vector3f

    /**
     * Taking this [Node]'s [boundingBox] into consideration, puts it above
     * the [position] entirely.
     */
    fun putAbove(position: Vector3f): Vector3f

    /**
     * Centers the [Node] on a given position.
     *
     * @param[position] - the position to center the [Node] on.
     * @return Vector3f - the center offset calculcated for the [Node].
     */
    fun centerOn(position: Vector3f): Vector3f

    /**
     *  This function rotates this spatial by a fixed [yaw] and [pitch] about the [target]
     *
     *  @param[yaw] yaw in degrees
     *  @param[pitch] pitch in degrees
     *  @param[target] the target position
     */
    fun rotateAround(yaw: Float, pitch: Float, target: Vector3f)

    /**
     * Orients the Node between points [p1] and [p2], and optionally
     * [rescale]s and [reposition]s it.
     */
    fun orientBetweenPoints(p1: Vector3f, p2: Vector3f, rescale: Boolean = false, reposition: Boolean = false): Quaternionf

    fun orientBetweenPoints(p1: Vector3f, p2: Vector3f, rescale: Boolean): Quaternionf {
        return orientBetweenPoints(p1, p2, rescale, false)
    }
    fun orientBetweenPoints(p1: Vector3f, p2: Vector3f): Quaternionf {
        return orientBetweenPoints(p1, p2, false, false)
    }

    fun <R> propertyChanged(property: KProperty<*>, old: R, new: R, custom: String = "")
    fun composeModel()
}

