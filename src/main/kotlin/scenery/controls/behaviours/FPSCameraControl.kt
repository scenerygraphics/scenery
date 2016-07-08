package scenery.controls.behaviours

import cleargl.GLVector
import org.scijava.ui.behaviour.DragBehaviour
import scenery.Camera

/**
 * FPS-style camera control
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @param[name] The name of the behaviour
 * @param[node] The node this behaviour controls
 * @param[w] Window width
 * @param[h] Window height
 * @constructor Creates a new FPSCameraControl behaviour
 */
open class FPSCameraControl(private val name: String, private val node: Camera, private val w: Int, private val h: Int) : DragBehaviour {
    /** default mouse x position in window */
    private var lastX = w / 2;
    /** default mouse y position in window */
    private var lastY = h / 2;
    /** whether this is the first entering event */
    private var firstEntered = true;

    /** pitch angle created from x/y position */
    private var pitch: Float = 0.0f;
    /** yaw angle created from x/y position */
    private var yaw: Float = 0.0f;

    init {
        val yp = node.forward.toYawPitch()
        this.yaw = yp.first
        this.pitch = yp.second
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
            yaw = Math.PI.toFloat();
        }

        pitch = Math.atan(Math.sqrt(1.0*dx*dx + 1.0*dy*dy)/dz).toFloat()

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
            lastX = x;
            lastY = y;
            firstEntered = false;
        }
    }

    /**
     * This function is called upon mouse down ends.
     *
     * @param[x] x position in window
     * @param[y] y position in window
     */
    override fun end(x: Int, y: Int) {
        firstEntered = true;
    }

    /**
     * This function is called during mouse down and updates the yaw and pitch states,
     * and resets the cam's forward and up vectors according to these angles.
     *
     * @param[x] x position in window
     * @param[y] y position in window
     */
    override fun drag(x: Int, y: Int) {
        var xoffset: Float = (x - lastX).toFloat();
        var yoffset: Float = (lastY - y).toFloat();

        lastX = x
        lastY = y

        xoffset *= 0.1f;
        yoffset *= 0.1f;

        yaw += xoffset;
        pitch += yoffset;

        if (pitch > 89.0f) {
            pitch = 89.0f;
        }
        if (pitch < -89.0f) {
            pitch = -89.0f;
        }

        val forward = GLVector(
                Math.cos(Math.toRadians(yaw.toDouble())).toFloat() * Math.cos(Math.toRadians(pitch.toDouble())).toFloat(),
                Math.sin(Math.toRadians(pitch.toDouble())).toFloat(),
                Math.sin(Math.toRadians(yaw.toDouble())).toFloat() * Math.cos(Math.toRadians(pitch.toDouble())).toFloat())

        node.forward = forward.normalized
    }


}
