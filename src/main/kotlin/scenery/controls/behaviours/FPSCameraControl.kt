package scenery.controls.behaviours

import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import org.scijava.ui.behaviour.DragBehaviour
import scenery.Camera

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class FPSCameraControl(private val name: String, private val node: Camera, private val w: Int, private val h: Int) : DragBehaviour {
    private var last = Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
    private var lastX = w / 2;
    private var lastY = h / 2;
    private var firstEntered = true;

    private var pitch: Float = 0.0f;
    private var yaw: Float = 0.0f;

    init {

    }

    override fun init(x: Int, y: Int) {
        if (firstEntered) {
            lastX = x;
            lastY = y;
            firstEntered = false;
        }
    }

    override fun end(x: Int, y: Int) {
        firstEntered = true;
    }

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
