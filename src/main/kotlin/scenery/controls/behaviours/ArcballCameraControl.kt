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
            distance = (value - node.position).magnitude()

            val yp = (value - node.position).toYawPitch()
            this.yaw = yp.first
            this.pitch = yp.second
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
        if (firstEntered) {
            lastX = x
            lastY = y
            firstEntered = false
        }

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

        xoffset *= 0.01f
        yoffset *= 0.01f

        yaw += xoffset
        pitch += yoffset

        if (pitch >= Math.PI.toFloat()/2.0f) {
            pitch = Math.PI.toFloat()/2.0f-0.01f
        }
        if (pitch <= -Math.PI.toFloat()/2.0f) {
            pitch = -Math.PI.toFloat()/2.0f+0.01f
        }

        val forward = GLVector(
            Math.cos((yaw.toDouble())).toFloat() * Math.cos((pitch.toDouble())).toFloat(),
            Math.sin((pitch.toDouble())).toFloat(),
            Math.sin((yaw.toDouble())).toFloat() * Math.cos((pitch.toDouble())).toFloat()
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
