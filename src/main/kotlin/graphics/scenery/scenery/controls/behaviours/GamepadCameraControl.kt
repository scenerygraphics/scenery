package graphics.scenery.scenery.controls.behaviours

import cleargl.GLVector
import net.java.games.input.Component
import graphics.scenery.scenery.Camera

/**
 * Implementation of GamepadBehaviour for Camera Control
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[name] Name of the behaviour
 * @property[axis] List of axis that are assigned to this behaviour
 * @property[node] The camera to control
 * @property[w] The window width
 * @property[h] The window height
 */
class GamepadCameraControl(private val name: String,
                           override val axis: List<Component.Identifier.Axis>,
                           private val node: Camera, private val w: Int, private val h: Int) : GamepadBehaviour {
    /** Last x position */
    private var lastX: Float = 0.0f
    /** Last y position */
    private var lastY: Float = 0.0f
    /** Is this the first event? */
    private var firstEntered = true;

    /** Pitch angle calculated from the axis position */
    private var pitch: Float = 0.0f;
    /** Yaw angle calculated from the axis position */
    private var yaw: Float = 0.0f;

    /** Multiplier of how big of a change the movement causes */
    private val speedMultiplier = 0.01f
    /** Threshold below which the behaviour will not trigger */
    private val threshold = 0.1f

    init {

    }

    /**
     * This function is trigger upon arrival of an axis event that
     * concerns this behaviour. It takes the event's value, as well as the
     * other axis' state to construct pitch and yaw angles and reorients
     * the camera.
     *
     * @param[axis] The gamepad axis.
     * @param[value] The absolute value of the gamepad axis.
     */
    @Synchronized
    override fun axisEvent(axis: Component.Identifier, value: Float) {
        if(Math.abs(value) < threshold) {
            return
        }

        var x: Float
        var y: Float

        if(axis == this.axis.first()) {
            x = value
            y = lastY
        } else {
            x = lastX
            y = value
        }

        if (firstEntered) {
            lastX = x
            lastY = y
            firstEntered = false;
        }

        var xoffset: Float = (x - lastX).toFloat();
        var yoffset: Float = (lastY - y).toFloat();

        lastX = x
        lastY = y

        xoffset *= 60f;
        yoffset *= 60f;

        yaw += xoffset;
        pitch += yoffset;

//        System.err.println("Yaw=$yaw, Pitch=$pitch, x=$x, y=$y")

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
