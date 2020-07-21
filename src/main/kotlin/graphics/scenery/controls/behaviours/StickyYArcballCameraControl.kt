package graphics.scenery.controls.behaviours

import org.joml.Vector3f
import graphics.scenery.Camera
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Quaternionf
import org.scijava.ui.behaviour.DragBehaviour
import org.scijava.ui.behaviour.ScrollBehaviour
import java.util.function.Supplier
import kotlin.reflect.KProperty

/**
 * Targeted ArcBall control with sticky y-axis
 *
 * This Behaviour provides ArcBall control for scenery, with a customizable target. If you
 * activate this behaviour or switch target, it'll be operating the camera from the current
 * distance to the current target.
 *
 * This Behaviour, when rotating the camera around the targeted node by dragging the mouse around,
 * will take the current screen y-axis at mouse drag init and will be using (and updating) this axis
 * as long as the mouse drag goes on. This y-axis will be subject to the tilt along screen x-axis
 * that is controlled by the user via the mouse up/down movement. So, the y-axis is variable because
 * drags can start with different initial y-axes (sticky to the screen's y-axis), yet it is fixed to
 * the node during the mouse drag (sticky to the node) -- thus the name "sticky".
 *
 * In whatever configuration the targeted node is at the beginning of the drag, the screen's y-axis
 * is considered to be the node's y-axis and the rotation revolves around it during the drag. When
 * drag is restarted, new current y-axis is taken.
 *
 * The Targeted ArcBall also provides [minimumDistance] and [maximumDistance] to clamp the distance
 * to the target to this range.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[name] The name of the behaviour
 * @property[camNode] The node this behaviour controls
 * @property[w] Window width
 * @property[h] Window height
 * @property[target] [Vector3f]-supplying with the look-at target of the arcball
 * @constructor Creates a new ArcballCameraControl behaviour
 */
open class StickyYArcballCameraControl(private val name: String, private val n: () -> Camera?, private val w: Int, private val h: Int, var target: () -> Vector3f) : DragBehaviour, ScrollBehaviour {
    private var lastX = w / 2
    private var lastY = h / 2
    private var firstEntered = true
    private val flexiYaxis = Vector3f()

    /** The [graphics.scenery.Node] this behaviour class controls */
    protected var camNode: Camera? by CameraDelegate()

    /** Camera delegate class, converting lambdas to Cameras. */
    protected inner class CameraDelegate {
        /** Returns the [graphics.scenery.Node] resulting from the evaluation of [n] */
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Camera? {
            return n.invoke()
        }

        /** Setting the value is not supported */
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Camera?) {
            throw UnsupportedOperationException()
        }
    }

    /** distance to target */
    var distance: Float = 5.0f
        set(value) {
            field = value

            camNode?.let { camNode -> camNode.position = target.invoke() + camNode.forward * value * (-1.0f) }
        }

    /** multiplier for zooming in and out */
    var scrollSpeedMultiplier = 0.005f
    /** multiplier for mouse movement */
    var mouseSpeedMultiplier = 0.1f
    /** minimum distance value to target */
    var minimumDistance = 0.0001f
    /** maximum distance value to target */
    var maximumDistance = Float.MAX_VALUE

    /**
     * Arcball camera control, supplying a Camera via a Java [Supplier] lambda.
     */
    @Suppress("unused")
    constructor(name: String, n: Supplier<Camera?>, w: Int, h: Int, target: Supplier<Vector3f>) : this(name, { n.get() }, w, h, { target.get() })

    /**
     * Arcball camera control, supplying a Camera via a Java [Supplier] lambda.
     * In this version, [target] is a static [Vector3f].
     */
    @Suppress("unused")
    constructor(name: String, n: () -> Camera?, w: Int, h: Int, target: Vector3f) : this(name, n, w, h, { target })

    /**
     * Arcball camera control, supplying a Camera via a Java [Supplier] lambda.
     * In this version, [target] is a static [Vector3f].
     */
    @Suppress("unused")
    constructor(name: String, n: Supplier<Camera?>, w: Int, h: Int, target: Vector3f) : this(name, { n.get() }, w, h, { target })

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
            flexiYaxis.set(0f,1f,0f)
            firstEntered = false
        }

        camNode?.targeted = true
        camNode?.target = target.invoke()
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
        camNode?.let { camNode ->
            if (!camNode.lock.tryLock()) {
                return
            }

            var xoffset: Float = (x - lastX).toFloat()
            var yoffset: Float = (y - lastY).toFloat()

            lastX = x
            lastY = y

            xoffset *= mouseSpeedMultiplier
            yoffset *= mouseSpeedMultiplier

            val frameYaw = xoffset / 180.0f * Math.PI.toFloat()
            val framePitch = yoffset / 180.0f * Math.PI.toFloat()

            // obtain pitch-rotation (along x-axis)
            val pitchQ = Quaternionf().rotateXYZ(framePitch, 0.0f, 0.0f)

            // calculate how the flexi-y-axis is currently tilted
            pitchQ.transform( flexiYaxis )

            // obtain "yaw"-rotation (along the flexi-y-axis)
            val yawQ = Quaternionf().rotateAxis( frameYaw, flexiYaxis )

            distance = (target.invoke() - camNode.position).length()
            camNode.target = target.invoke()
            camNode.rotation = pitchQ.mul(camNode.rotation).premul(yawQ).normalize()
            camNode.position = target.invoke() + camNode.forward * distance * (-1.0f)

            camNode.lock.unlock()
        }
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
        if (isHorizontal || camNode == null) {
            return
        }

        distance = (target.invoke() - camNode!!.position).length()
        distance += wheelRotation.toFloat() * scrollSpeedMultiplier

        if (distance >= maximumDistance) distance = maximumDistance
        if (distance <= minimumDistance) distance = minimumDistance

        camNode?.let { camNode -> camNode.position = target.invoke() + camNode.forward * distance * (-1.0f) }
    }

}
