package graphics.scenery.controls.behaviours

import graphics.scenery.BoundingGrid
import graphics.scenery.Camera
import graphics.scenery.Scene
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.LazyLogger
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.reflect.KProperty

/**
 * Raycasting-based selection command. Needs to be given a
 * [scene] to act upon, plus a lambda returning current camera information ([camera]).
 *
 * The command returns all the selected objects sorted by distance to
 * the lambda given in [action]. [ignoredObjects] can be set to classes the user does not want
 * to select, by default this is only [BoundingGrid].
 *
 * If [debugRaycast] is true, a line will be drawn from the camera in the direction of
 * the selection raycast.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class SelectCommand @JvmOverloads constructor(protected val name: String,
                                                   protected val renderer: Renderer,
                                                   protected val scene: Scene,
                                                   camera: () -> Camera?,
                                                   protected var debugRaycast: Boolean = false,
                                                   var ignoredObjects: List<Class<*>> = listOf<Class<*>>(BoundingGrid::class.java),
                                                   protected var action: ((Scene.RaycastResult, Int, Int) -> Unit) = { _, _, _ -> Unit }) : ClickBehaviour,
    WithCameraDelegateBase(camera) {
    protected val logger by LazyLogger()


    /**
     * This is the action executed upon triggering this action, with [x] and [y] being
     * the screen-space coordinates.
     */
    override fun click(x: Int, y: Int) {
        cam?.let { cam ->
            val matches = cam.getNodesForScreenSpacePosition(x, y, ignoredObjects, debugRaycast)
            action.invoke(matches, x, y)
        }
    }
}
