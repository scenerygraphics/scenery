package graphics.scenery.controls.behaviours

import com.jogamp.opengl.math.FloatUtil.sqrt
import org.joml.Vector3f
import graphics.scenery.Camera
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Quaternionf
import org.scijava.ui.behaviour.DragBehaviour
import org.scijava.ui.behaviour.ScrollBehaviour
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.reflect.KProperty

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
 * @property[target] [Vector3f]-supplying with the look-at target of the arcball
 * @constructor Creates a new ArcballCameraControl behaviour
 */
open class ArcballCameraControl(private val name: String, private val n: () -> Camera?, private val w: Int, private val h: Int, var target: () -> Vector3f) : DragBehaviour, ScrollBehaviour {
    private val logger by LazyLogger()
    private var lastX = w / 2
    private var lastY = h / 2
    private var firstEntered = true

    /** The [graphics.scenery.Node] this behaviour class controls */
    protected var node: Camera? by CameraDelegate()

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

            node?.let { node -> node.position = target.invoke() + node.forward * value * (-1.0f) }
        }

    /** multiplier for zooming in and out */
    var scrollSpeedMultiplier = 0.005f
    /** multiplier for mouse movement */
    var mouseSpeedMultiplier = 1.0f
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
            firstEntered = false
        }

        node?.targeted = true
        node?.target = target.invoke()
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
        node?.let { node ->
            if (!node.lock.tryLock()) {
                return
            }

            val xoffset: Float = (x - lastX).toFloat()
            val yoffset: Float = (lastY - y).toFloat()

            val axis = if(abs(xoffset) > abs(yoffset)) {
                Vector3f(0.0f, 1.0f, 0.0f)
            } else {
                Vector3f(1.0f, 0.0f, 0.0f)
            }

            val speed = 1.5f * mouseSpeedMultiplier
            val p1 = xyToPoint((lastX.toFloat()-w/2.0f)/(w*speed), -(lastY.toFloat()-h/2.0f)/(h*speed), axis)
            val p2 = xyToPoint((x.toFloat()-w/2.0f)/(w*speed), -(y.toFloat()-h/2.0f)/(h*speed), axis)

            lastX = x
            lastY = y

            val dot = p1.dot(p2)
            val tmp = p1.cross(p2)
            val q = Quaternionf(tmp.x, tmp.y, tmp.z, dot)

            distance = (target.invoke() - node.position).length()
            node.target = target.invoke()
            node.rotation = q.mul(node.rotation)
            node.position = target.invoke() + node.forward * distance * (-1.0f)

            node.lock.unlock()
        }
    }

    private fun xyToPoint(x: Float, y: Float, axis: Vector3f): Vector3f {
        val p = Vector3f(x.toFloat(), y.toFloat(), 0.0f)
        val r = p.x*p.x + p.y*p.y

        if(r > 1.0f) {
            val s = 1.0f/sqrt(r)
            p.mul(s)
        } else {
            p.set(p.x, p.y, sqrt(1.0f - r))
        }

        val dot = p.dot(axis)
        val proj = p - Vector3f(axis).mul(dot)
        val norm = proj.length()

        return when {
            norm > 0.0f -> {
                logger.info("Positive norm!")
                var s = 1.0f/norm
                if(proj.z < 0.0f) {
                    s *= -1.0f
                }

                Vector3f(proj).mul(s)
            }
            axis.z == 1.0f -> {
                logger.info("Z axis!")
                Vector3f(1.0f, 0.0f, 0.0f)
            }
            else -> {
                logger.info("Other case!")
                Vector3f(-axis.x, axis.y, 0.0f).normalize()
            }
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
        if (isHorizontal) {
            return
        }

        distance += wheelRotation.toFloat() * scrollSpeedMultiplier

        if (distance >= maximumDistance) distance = maximumDistance
        if (distance <= minimumDistance) distance = minimumDistance

        node?.let { node -> node.position = target.invoke() + node.forward * distance * (-1.0f) }
    }

}
