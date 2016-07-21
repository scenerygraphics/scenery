package scenery.controls.behaviours

import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import org.scijava.ui.behaviour.DragBehaviour
import org.scijava.ui.behaviour.ScrollBehaviour
import scenery.Camera

/**
 * Targeted ArcBall control
 *
 * This Behaviour provides ArcBall control for scenery, with a customizable target. If you
 * activate this behaviour, it'll use the current camera distance to the target as initial distance.
 *
 * The Targeted ArcBall also provides [minimumDistance] and [maximumDistance] to clamp the distance
 * to the target to this range.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[name] The name of the behaviour
 * @property[node] The node this behaviour controls
 * @property[w] Window width
 * @property[h] Window height
 * @property[target] Vector with the look-at target of the arcball
 * @constructor Creates a new ArcballCameraControl behaviour
 */
open class ArcballCameraControl(private val name: String, private val node: Camera, private var w: Int, private var h: Int, target: GLVector = GLVector(0.0f, 0.0f, 0.0f)) : DragBehaviour, ScrollBehaviour {
    /** distance to target */
    private var distance: Float = 5.0f
    /** multiplier for zooming in and out */
    var scrollSpeedMultiplier = 0.05f
    /** minimum distance value to target */
    var minimumDistance = 0.0001f
    /** maximum distance value to target */
    var maximumDistance = Float.MAX_VALUE
    /** state for start vector */
    var start: GLVector = GLVector(0.0f, 0.0f, 0.0f)
    /** state for end vector */
    var end: GLVector = GLVector(0.0f, 0.0f, 0.0f)
    /** mouse bounds */
    var bounds: FloatArray = floatArrayOf(0.0f, 0.0f)
    /** target of the camera */
    var target: GLVector = GLVector(0.0f, 0.0f, 0.0f)
        set(value) {
            field = value

            node.target = value
            distance = (value - node.position).magnitude()

            val yp = (value - node.position).toYawPitch()
        }

    init {
        val yp = (node.forward-node.position).toYawPitch()
        this.target = target
        this.bounds = floatArrayOf(1.0f/((w-1.0f)*0.5f), 1.0f/((h-1.0f)*0.5f))

        node.target = target
        node.targeted = true
    }

    /**
     * This extension function creates yaw/pitch angles from
     * a given GLVector.
     *
     * @return A Pair consisting of the yaw and pitch angles
     */
    protected fun GLVector.toYawPitch(): Pair<Float, Float> {
        val dx = this.normalized.x()
        val dy = this.normalized.y()
        val dz = this.normalized.z()

        val yaw = Math.atan(1.0*dx/(-1.0*dy)).toFloat()
        val pitch = Math.atan(Math.sqrt(1.0*dx*dx+dy*dy)/dz).toFloat()

        return Pair(yaw, pitch)
    }

    /**
     * This function is called upon mouse down and initialises the camera control
     * with the current window size.
     *
     * @param[x] x position in window
     * @param[y] y position in window
     */
    override fun init(x: Int, y: Int) {
        node.target = target
        node.targeted = true

        this.start = mapToSphere(x.toFloat(), y.toFloat())
    }

    /**
     * This function is called upon mouse down ends.
     *
     * @param[x] x position in window
     * @param[y] y position in window
     */
    override fun end(x: Int, y: Int) {
    }

    /**
     * This function is called during mouse down and updates the yaw and pitch states,
     * and resets the cam's forward and up vectors according to these angles.
     *
     * @param[x] x position in window
     * @param[y] y position in window
     */
    override fun drag(x: Int, y: Int) {
        end = mapToSphere(x.toFloat(), y.toFloat())

        val perpendicular = end.cross(start)

        val rot = if(perpendicular.magnitude() > 10e-5f) {
            Quaternion(perpendicular.x(), perpendicular.y(), perpendicular.z(), start*end)
        } else {
            Quaternion().setIdentity()
        }

        rot.mult(node.rotation)
        node.rotation = rot

        start = end
    }

    protected fun mapToSphere(x: Float, y: Float): GLVector {
        // scale to bounds
        val scaledPoint = GLVector(x * bounds[0] - 1.0f, 1.0f - y * bounds[1], 0.0f)
        val length2 = scaledPoint.length2()

        return if (length2 > 1.0f) {
            GLVector(scaledPoint.x(), scaledPoint.y(), 0.0f)*(1.0f/Math.sqrt(1.0*length2).toFloat())
        } else {
            GLVector(scaledPoint.x(), scaledPoint.y(), Math.sqrt(1.0 - length2).toFloat())
        }.normalized
    }

    /**
     * The scroll function is called when a scroll event is detected and will change
     * the [distance] according to the scroll direction and bound by the [minimumDistance] and
     * [maximumDistance] values.
     *
     * @param[wheelRotation] Absolute rotation value of the mouse wheel
     * @param[isHorizontal] Whether the scroll event is horizontal. We use only vertical events.
     * @param [x] unused
     * @param[y] unused
     */
    override fun scroll(wheelRotation: Double, isHorizontal: Boolean, x: Int, y: Int) {
        if (isHorizontal) {
            return
        }

        distance += wheelRotation.toFloat() * scrollSpeedMultiplier

        if (distance >= maximumDistance) distance = maximumDistance
        if (distance <= minimumDistance) distance = minimumDistance

        node.position = target + node.forward * distance * (-1.0f)
    }

}
