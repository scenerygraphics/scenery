package graphics.scenery.controls.behaviours

import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import org.scijava.ui.behaviour.DragBehaviour
import graphics.scenery.Camera
import kotlin.reflect.KProperty

/**
 * FPS-style camera control
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[name] The name of the behaviour
 * @property[node] The node this behaviour controls
 * @property[w] Window width
 * @property[h] Window height
 * @constructor Creates a new FPSCameraControl behaviour
 */
open class FPSCameraControl(private val name: String, private val n: () -> Camera?, private val w: Int, private val h: Int) : DragBehaviour {
    /** default mouse x position in window */
    private var lastX = w / 2
    /** default mouse y position in window */
    private var lastY = h / 2
    /** whether this is the first entering event */
    private var firstEntered = true

    private var node: Camera? by CameraDelegate()

    inner class CameraDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Camera? {
            return n.invoke()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Camera?) {
            throw UnsupportedOperationException()
        }
    }

    init {
        node?.targeted = false
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
        val pitch: Float

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

        pitch = Math.atan(Math.sqrt(1.0*dx*dx + 1.0*dy*dy)/dz).toFloat()

        return Pair((-yaw * 180.0f / Math.PI.toFloat()), pitch)
    }

    /**
     * This function is called upon mouse down and initialises the camera control
     * with the current window size.
     *
     * @param[x] x position in window
     * @param[y] y position in window
     */
    override fun init(x: Int, y: Int) {
        node?.targeted = false
        if (firstEntered) {
            lastX = x
            lastY = y
            firstEntered = false
        }
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
    @Synchronized override fun drag(x: Int, y: Int) {
        if(!(node?.lock?.tryLock() ?: false)) {
            return
        }

        var xoffset: Float = (x - lastX).toFloat()
        var yoffset: Float = (y - lastY).toFloat()

        lastX = x
        lastY = y

        xoffset *= 0.1f
        yoffset *= 0.1f

        val frameYaw = xoffset
        val framePitch = yoffset

        val yawQ = Quaternion().setFromEuler(0.0f, frameYaw/180.0f*Math.PI.toFloat(), 0.0f)
        val pitchQ = Quaternion().setFromEuler(framePitch/180.0f*Math.PI.toFloat(), 0.0f, 0.0f)
        node?.rotation = pitchQ.mult(node?.rotation).mult(yawQ).normalize()

        node?.lock?.unlock()
    }


}
