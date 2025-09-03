package graphics.scenery.controls.behaviours

import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import net.java.games.input.Component
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.reflect.KProperty

/**
 * Implementation of GamepadBehaviour for Camera Movement Control
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[axis] List of axis that are assigned to this behaviour
 * @property[cam] The camera to control
 */
open class GamepadMovementControl(
                             override val axis: List<Component.Identifier>,
                             var invertX: Boolean = false,
                             var invertY: Boolean = false,
                             private val node: () -> Node?) : GamepadBehaviour {
    /** Speed multiplier for camera movement */
    var speedMultiplier = 0.01f
    /** Threshold below which the behaviour does not trigger */
    var threshold = 0.05f

    private val cam: Node? by NodeDelegate()

    private var inversionFactorX = 1.0f
    private var inversionFactorY = 1.0f

    protected inner class NodeDelegate {
        /** Returns the [graphics.scenery.Camera] resulting from the evaluation of [node] */
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Node? {
            return node.invoke()
        }

        /** Setting the value is not supported */
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Node?) {
            throw UnsupportedOperationException()
        }
    }

    /**
     * This function is triggered upon arrival of an axis event that
     * concerns this behaviour. It takes the event's value to move the camera
     * in the corresponding direction.
     *
     * @param[axis] The gamepad axis.
     * @param[value] The absolute value of the gamepad axis.
     */
    @Synchronized
    override fun axisEvent(axis: Component.Identifier, value: Float) {
        cam?.let { cam ->
            if (abs(value) < threshold) {
                return
            }

            inversionFactorX = if(invertX) {
                -1.0f
            } else {
                1.0f
            }

            inversionFactorY = if(invertY) {
                -1.0f
            } else {
                1.0f
            }
            cam.ifSpatial {
                if(cam is Camera) {
                    when (axis) {
                        Component.Identifier.Axis.X -> {
                            position += Vector3f(cam.forward).cross(cam.up).normalize() * value * speedMultiplier * inversionFactorX
                        }
                        Component.Identifier.Axis.Y -> {
                            position += cam.forward * -1.0f * value * speedMultiplier * inversionFactorY
                        }
                        Component.Identifier.Axis.Z -> {
                            position += cam.up * value * speedMultiplier
                        }
                    }
                } else {
                    when (axis) {
                        Component.Identifier.Axis.X -> {
                            position += Vector3f(1.0f, 0.0f, 0.0f) * value * speedMultiplier * inversionFactorX
                        }
                        Component.Identifier.Axis.Y -> {
                            position += Vector3f(0.0f, 0.0f, -1.0f) * -1.0f * value * speedMultiplier * inversionFactorY
                        }
                        Component.Identifier.Axis.Z -> {
                            position += Vector3f(0.0f, 1.0f, 0.0f) * value * speedMultiplier
                        }
                    }
                }
            }
        }
//        System.err.println("Camera.position=${cam.position.x()}/${cam.position.y()}/${cam.position.z()}")
    }

}

