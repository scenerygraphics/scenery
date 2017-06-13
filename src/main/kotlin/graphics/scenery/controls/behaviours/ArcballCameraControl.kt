package graphics.scenery.controls.behaviours

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import org.scijava.ui.behaviour.DragBehaviour
import org.scijava.ui.behaviour.ScrollBehaviour
import graphics.scenery.Camera

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
    var pitch: Float = 0.0f
        private set
    /** yaw angle created from x/y position */
    var yaw: Float = 0.0f
        private set
    /** distance to target */
    var distance: Float = 5.0f
        set(value) {
            field = value
            node.position = target + node.forward * value * (-1.0f)
        }

    /** multiplier for zooming in and out */
    var scrollSpeedMultiplier = 0.005f
    /** multiplier for mouse movement */
    var mouseSpeedMultiplier = 0.1f
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
        }

    init {
        this.target = target

        node.target = target
        node.targeted = true
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

        node.targeted = true
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

        xoffset *= mouseSpeedMultiplier
        yoffset *= -mouseSpeedMultiplier

        yaw = xoffset/180.0f*Math.PI.toFloat()
        pitch = yoffset/180.0f*Math.PI.toFloat()

        // first calculate the total rotation quaternion to be applied to the camera
        val rot = Quaternion().setFromEuler(pitch, yaw, 0.0f)
        rot.mult(node.rotation).normalize()
        node.rotation = rot

        // then use it to generate a new forward vector for correct positioning
        val m = GLMatrix.fromQuaternion(node.rotation)
        node.forward = GLVector(m.get(0, 2), m.get(1, 2), m.get(2, 2)).normalize() * -1.0f
        node.position = target + node.forward * distance * (-1.0f)
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
