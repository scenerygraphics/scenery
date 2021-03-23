package graphics.scenery.controls.behaviours

import graphics.scenery.BoundingGrid
import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import org.scijava.ui.behaviour.DragBehaviour
import kotlin.reflect.KProperty

/**
 * Drag nodes roughly along a sphere around the camera by mouse.
 * Implements algorithm from https://forum.unity.com/threads/implement-a-drag-and-drop-script-with-c.130515/
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
open class MouseDragSphere(
    protected val name: String,
    camera: () -> Camera?,
    protected var debugRaycast: Boolean = false,
    var ignoredObjects: List<Class<*>> = listOf<Class<*>>(BoundingGrid::class.java)
) : DragBehaviour, WithCameraDelegateBase(camera) {

    protected val logger by LazyLogger()

    protected var currentNode: Node? = null
    protected var distance: Float = 0f

    override fun init(x: Int, y: Int) {
        cam?.let { cam ->
            val matches = cam.getNodesForScreenSpacePosition(x, y, ignoredObjects, debugRaycast)
            currentNode = matches.matches.firstOrNull()?.node

            distance = currentNode?.position?.distance(cam.position) ?: 0f
        }
    }

    override fun drag(x: Int, y: Int) {
        if (distance <= 0)
            return

        cam?.let {
            val (rayStart, rayDir) = it.screenPointToRay(x, y)
            rayDir.normalize()
            val newPos = rayStart + rayDir * distance

            currentNode?.position = newPos
        }
    }

    override fun end(x: Int, y: Int) {}
}
