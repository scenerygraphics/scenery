package scenery.controls.behaviours

import cleargl.GLVector
import org.scijava.ui.behaviour.DragBehaviour
import scenery.Camera

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class FPSCameraControl(private val name: String, private val node: Camera, private val w: Int, private val h: Int) : DragBehaviour {
    private var lastX = w / 2;
    private var lastY = h / 2;
    private var firstEntered = true;

    private var pitch: Float = 0.0f;
    private var yaw: Float = 0.0f;

    init {
        val yp = node.forward.toYawPitch()
        this.yaw = yp.first
        this.pitch = yp.second
    }

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
