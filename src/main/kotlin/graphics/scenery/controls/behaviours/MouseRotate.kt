package graphics.scenery.controls.behaviours

import graphics.scenery.BoundingGrid
import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.utils.LazyLogger
import org.joml.Quaternionf
import org.scijava.ui.behaviour.DragBehaviour
import kotlin.reflect.KProperty

/**
 * Control behavior for rotating a Node
 *
 * @author Vladimir Ulman
 * @author Jan Tiemann
 */
open class MouseRotate(
    protected val name: String,
    protected val camera: () -> Camera?,
    protected val alternativeTargetNode: (() -> Node?)? = null,
    protected var debugRaycast: Boolean = false,
    protected var ignoredObjects: List<Class<*>> = listOf<Class<*>>(BoundingGrid::class.java),
    protected val mouseSpeed: () -> Float = { 0.25f }
) : DragBehaviour {

    protected val logger by LazyLogger()
    protected val cam: Camera? by CameraDelegate()

    protected var currentNode: Node? = null
    private var lastX = 0
    private var lastY = 0


    /** Camera delegate class, converting lambdas to Cameras. */
    protected inner class CameraDelegate {
        /** Returns the [graphics.scenery.Camera] resulting from the evaluation of [camera] */
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Camera? {
            return camera.invoke()
        }

        /** Setting the value is not supported */
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Camera?) {
            throw UnsupportedOperationException()
        }
    }

    /**
     * This function is called upon mouse down and initializes the camera control
     * with the current window size.
     *
     * x position in window
     * y position in window
     */
    override fun init(x: Int, y: Int) {
        if (alternativeTargetNode != null) {
            currentNode = alternativeTargetNode.invoke()
        } else {
            cam?.let { cam ->
                val matches = cam.getNodesForScreenSpacePosition(x, y, ignoredObjects, debugRaycast)
                currentNode = matches.matches.firstOrNull()?.node
            }
        }

        lastX = x
        lastY = y
    }

    override fun drag(x: Int, y: Int) {
        val targetedNode = currentNode

        cam?.let {
            if (targetedNode == null || !targetedNode.lock.tryLock()) return

            val frameYaw = mouseSpeed() * (x - lastX) * 0.0174533f // 0.017 = PI/180
            val framePitch = mouseSpeed() * (y - lastY) * 0.0174533f

            Quaternionf().rotateAxis(frameYaw, it.up)
                .mul(targetedNode.rotation, targetedNode.rotation)
                .normalize()
            Quaternionf().rotateAxis(framePitch, it.right)
                .mul(targetedNode.rotation, targetedNode.rotation)
                .normalize()
            targetedNode.needsUpdate = true

            targetedNode.lock.unlock()

            lastX = x
            lastY = y
        }
    }

    override fun end(x: Int, y: Int) {
    }
}
