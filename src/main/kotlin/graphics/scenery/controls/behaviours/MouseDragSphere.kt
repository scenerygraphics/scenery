package graphics.scenery.controls.behaviours

import graphics.scenery.BoundingGrid
import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.utils.lazyLogger
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour
import kotlin.math.atan2

/**
 * Drag nodes roughly along a sphere around the camera by mouse.
 * Implements algorithm from https://forum.unity.com/threads/implement-a-drag-and-drop-script-with-c.130515/
 * @param [name] Name of the behavior
 * @param [camera] The camera to use
 * @param [filter] Ignore nodes for the raycast for nodes it retuns false for
 * @param [rotateAroundCenter] Rotates the object around the world center instead of the camera. Defaults to false.
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
open class MouseDragSphere(
    protected val name: String,
    camera: () -> Camera?,
    protected var debugRaycast: Boolean = false,
    var filter: (Node) -> Boolean,
    private var rotateAroundCenter: Boolean = false,
) : DragBehaviour, WithCameraDelegateBase(camera) {

    protected val logger by lazyLogger()

    protected var currentNode: Node? = null
    protected var currentHit: Vector3f = Vector3f()
    protected var distance: Float = 0f

    constructor(
        name: String,
        camera: () -> Camera?,
        debugRaycast: Boolean = false,
        ignoredObjects: List<Class<*>> = listOf<Class<*>>(BoundingGrid::class.java),
        rotateAroundCenter: Boolean = false
    ) : this(name, camera, debugRaycast, { n: Node ->
        !ignoredObjects.any { it.isAssignableFrom(n.javaClass) }}, rotateAroundCenter)


    override fun init(x: Int, y: Int) {
        cam?.let { cam ->
            val matches = cam.getNodesForScreenSpacePosition(x, y, filter, debugRaycast)
            currentNode = matches.matches.firstOrNull()?.node
            distance =
                matches.matches.firstOrNull()?.distance ?: 0f//currentNode?.position?.distance(cam.position) ?: 0f

            val (rayStart, rayDir) = cam.screenPointToRay(x, y)
            rayDir.normalize()
            currentHit = rayStart + rayDir * distance
        }
    }

    override fun drag(x: Int, y: Int) {
        if (distance <= 0)
            return

        cam?.let { cam ->
            currentNode?.let {
                val (rayStart, rayDir) = cam.screenPointToRay(x, y)
                rayDir.normalize()
                val newHit = rayStart + rayDir * distance
                val movement = newHit - currentHit

                it.ifSpatial {
                    val newPos = if (rotateAroundCenter) {
                        // Calculate the rotation around (0, 0, 0)
                        val currentPos = position / worldScale()
                        val axis = currentPos.cross(movement, Vector3f()).normalize()
                        val angle = atan2(movement.length(), currentPos.length())//currentPos.angle(center)
                        val rotationQuaternion = Quaternionf().identity().rotateAxis(angle, axis)

                        rotationQuaternion.transform(currentPos, Vector3f())
                    } else {
                        // Rotation around camera's center
                        position + movement / worldScale()
                    }
                    currentNode?.spatialOrNull()?.position = newPos
                    currentHit = newHit
                }
            }
        }
    }

    override fun end(x: Int, y: Int) {
        // intentionally empty. A new click will overwrite the running variables.
    }
}
