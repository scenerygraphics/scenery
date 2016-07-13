package scenery.controls.behaviours

import cleargl.GLVector
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
open class ArcballCameraControl(private val name: String, private val node: Camera, private val w: Int, private val h: Int, target: GLVector = GLVector(0.0f, 0.0f, 0.0f)) : DragBehaviour, ScrollBehaviour {
    /** default mouse x position in window */
    private var lastX = w / 2
    /** default mouse y position in window */
    private var lastY = h / 2
    /** whether this is the first entering event */
    private var firstEntered = true

    /** pitch angle created from x/y position */
    private var pitch: Float = 0.0f
    /** yaw angle created from x/y position */
    private var yaw: Float = 0.0f
    /** distance to target */
    private var distance: Float = 5.0f
    /** multiplier for zooming in and out */
    var scrollSpeedMultiplier = 0.05f
    /** minimum distance value to target */
    var minimumDistance = 0.0001f
    /** maximum distance value to target */
    var maximumDistance = Float.MAX_VALUE
    /** target of the camera */
    var target: GLVector = GLVector(0.0f, 0.0f, 0.0f)
        set(value) {
            field = value

            node.target = value
            distance = (value- node.position).magnitude()
        }

    init {
        val yp = (node.forward-node.position).toYawPitch()
        this.yaw = yp.first
        this.pitch = yp.second
        this.target = target

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
        val dx = this.x()
        val dy = this.y()
        val dz = this.z()
        var yaw: Float = 0.0f
        var pitch: Float

        if (Math.abs(dx) < 0.000001f) {
            if (dx < 0.0f) {
                yaw = 1.5f * Math.PI.toFloat()
            } else {
                yaw = 0.5f * Math.PI.toFloat()
            }

            yaw -= Math.atan((1.0 * dz) / (1.0 * dx)).toFloat()
        } else if (dz < 0) {
            yaw = Math.PI.toFloat()
        }

        pitch = Math.atan(Math.sqrt(1.0 * dx * dx + 1.0 * dy * dy) / dz).toFloat()

        return Pair((-yaw * 180.0f / Math.PI.toFloat() - 90.0f), pitch)
    }

    /**
     * This function is called upon mouse down and initialises the camera control
     * with the current window size.
     *
     * @param[x] x position in window
     * @param[y] y position in window
     */
    override fun init(x: Int, y: Int) {
        if (firstEntered) {
            lastX = x
            lastY = y
            firstEntered = false
        }

        System.err.println("TargetArcball: Mouse down, target set to ${this.target}")
        node.target = target
    }

    /**
     * This function is called upon mouse down ends.
     *
     * @param[x] x position in window
     * @param[y] y position in window
     */
    override fun end(x: Int, y: Int) {
        firstEntered = true
    }

    /**
     * This function is called during mouse down and updates the yaw and pitch states,
     * and resets the cam's forward and up vectors according to these angles.
     *
     * @param[x] x position in window
     * @param[y] y position in window
     */
    override fun drag(x: Int, y: Int) {
        var xoffset: Float = (x - lastX).toFloat()
        var yoffset: Float = (lastY - y).toFloat()

        lastX = x
        lastY = y

        xoffset *= 0.1f
        yoffset *= 0.1f

        yaw += xoffset
        pitch += yoffset

        if (pitch > 89.0f) {
            pitch = 89.0f
        }
        if (pitch < -89.0f) {
            pitch = -89.0f
        }

        val forward = GLVector(
            Math.cos(Math.toRadians(yaw.toDouble())).toFloat() * Math.cos(Math.toRadians(pitch.toDouble())).toFloat(),
            Math.sin(Math.toRadians(pitch.toDouble())).toFloat(),
            Math.sin(Math.toRadians(yaw.toDouble())).toFloat() * Math.cos(Math.toRadians(pitch.toDouble())).toFloat()
        ).normalized

        val position = forward * distance * (-1.0f)
        node.position = target + position
        node.forward = forward
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
