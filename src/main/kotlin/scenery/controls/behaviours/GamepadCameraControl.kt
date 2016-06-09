package scenery.controls.behaviours

import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import net.java.games.input.Component
import scenery.Camera

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class GamepadCameraControl(private val name: String,
                           override val axis: List<Component.Identifier.Axis>,
                           private val node: Camera, private val w: Int, private val h: Int) : GamepadBehaviour {
    private var last = Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
    private var lastX: Float = 0.0f
    private var lastY: Float = 0.0f
    private var firstEntered = true;

    private var pitch: Float = 0.0f;
    private var yaw: Float = 0.0f;

    private val speedMultiplier = 0.01f
    private val threshold = 0.1f

    init {

    }

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
