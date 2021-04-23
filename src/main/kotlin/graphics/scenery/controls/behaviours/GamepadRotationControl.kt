package graphics.scenery.controls.behaviours

import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.plus
import net.java.games.input.Component
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.reflect.KProperty

/**
 * Implementation of GamepadBehaviour for Camera Control
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[axis] List of axis that are assigned to this behaviour
 * @property[node] The node to control
 * @property[sensitivity] A multiplier applied to axis inputs.
 */
open class GamepadRotationControl(override val axis: List<Component.Identifier>,
                                  var sensitivity: Float = 1.0f,
                                  private val n: () -> Node?) : GamepadBehaviour {
    private var lastX: Float = 0.0f
    private var lastY: Float = 0.0f
    private var firstEntered = true
    private val logger by LazyLogger()

    /** The [graphics.scenery.Node] this behaviour class controls */
    protected var node: Node? by NodeDelegate()

    protected inner class NodeDelegate {
        /** Returns the [graphics.scenery.Node] resulting from the evaluation of [n] */
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Node? {
            return n.invoke()
        }

        /** Setting the value is not supported */
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Node?) {
            throw UnsupportedOperationException()
        }
    }

    /** Pitch angle calculated from the axis position */
    private var pitch: Float = 0.0f
    /** Yaw angle calculated from the axis position */
    private var yaw: Float = 0.0f

    /** Threshold below which the behaviour will not trigger */
    var threshold = 0.05f

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
        val n = node ?: return

        if(abs(value) < threshold) {
            return
        }
        
        val x: Float
        val y: Float

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
            firstEntered = false
        }

        val xoffset: Float = x * sensitivity
        val yoffset: Float = y * sensitivity

        lastX = x
        lastY = y

        yaw += xoffset
        pitch += yoffset

        val frameYaw = xoffset / 180.0f * Math.PI.toFloat()
        val framePitch = yoffset / 180.0f * Math.PI.toFloat()
        logger.trace("Pitch={} Yaw={}", framePitch, frameYaw)

        if(n is Camera) {
            if (pitch > 89.0f) {
                pitch = 89.0f
            }
            if (pitch < -89.0f) {
                pitch = -89.0f
            }

            val yawQ = Quaternionf().rotateXYZ(0.0f, frameYaw, 0.0f)
            val pitchQ = Quaternionf().rotateXYZ(framePitch, 0.0f, 0.0f)

            n.rotation = pitchQ.mul(n.rotation).mul(yawQ).normalize()
        } else {
            if(axis != this.axis.first()) {
                n.rotation = n.rotation.rotateLocalY(framePitch).normalize()
            } else {
                n.rotation = n.rotation.rotateLocalX(frameYaw).normalize()
            }
        }
    }
}
