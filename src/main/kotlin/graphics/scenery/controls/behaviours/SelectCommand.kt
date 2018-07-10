package graphics.scenery.controls.behaviours

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.LazyLogger
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.reflect.KProperty

/**
 * Raycasting-based selection command. Needs to be attached to a [renderer], and given a
 * [scene] to act upon, plus a lambda returning camera information ([camera]).
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
                                                   protected val camera: () -> Camera?,
                                                   protected var debugRaycast: Boolean = false,
                                                   var ignoredObjects: List<Class<*>> = listOf<Class<*>>(BoundingGrid::class.java),
                                                   protected var action: ((List<SelectResult>) -> Unit) = {}) : ClickBehaviour {
    protected val logger by LazyLogger()

    protected val cam: Camera? by CameraDelegate()

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
     * Data class for selection results, contains the [Node] as well as the distance
     * from the observer to it.
     */
    data class SelectResult(val node: Node, val distance: Float)

    /**
     * This is the action executed upon triggering this action, with [x] and [y] being
     * the screen-space coordinates.
     */
    override fun click(x: Int, y: Int) {
        cam?.let { cam ->
            val view = (cam.target - cam.position).normalize()
            var h = view.cross(cam.up).normalize()
            var v = h.cross(view)

            val width = renderer.window.width.toFloat()
            val height = renderer.window.height.toFloat()

            val fov = cam.fov * Math.PI / 180.0f
            val lengthV = Math.tan(fov / 2.0).toFloat() * cam.nearPlaneDistance
            val lengthH = lengthV * (width / height)

            v *= lengthV
            h *= lengthH

            val posX = (x - width / 2.0f) / (width / 2.0f)
            val posY = -1.0f * (y - height / 2.0f) / (height / 2.0f)

            val worldPos = cam.position + view * cam.nearPlaneDistance + h * posX + v * posY
            val worldDir = (worldPos - cam.position).normalized

            if (debugRaycast) {
                val indicatorMaterial = Material()
                indicatorMaterial.diffuse = GLVector(1.0f, 0.2f, 0.2f)
                indicatorMaterial.specular = GLVector(1.0f, 0.2f, 0.2f)
                indicatorMaterial.ambient = GLVector(0.0f, 0.0f, 0.0f)

                for(it in 5..50) {
                    val s = Box(GLVector(0.08f, 0.08f, 0.08f))
                    s.material = indicatorMaterial
                    s.position = worldPos + worldDir * it.toFloat()
                    scene.addChild(s)
                }
            }

            val matches = scene.discover(scene, { node ->
                node.visible && !ignoredObjects.contains(node.javaClass)
            }).map {
                Pair(it, it.intersectAABB(worldPos, worldDir))
            }.filter {
                it.second.first && it.second.second > 0.0f
            }.map {
                SelectResult(it.first, it.second.second)
            }.sortedBy {
                it.distance
            }

            if (debugRaycast) {
                logger.info(matches.joinToString(", ") { "${it.node.name} at distance ${it.distance}" })

                val m = Material()
                m.diffuse = GLVector(1.0f, 0.0f, 0.0f)
                m.specular = GLVector(0.0f, 0.0f, 0.0f)
                m.ambient = GLVector(0.0f, 0.0f, 0.0f)
                m.needsTextureReload = true

                matches.firstOrNull()?.let {
                    it.node.material = m
                }
            }

            action.invoke(matches)
        }
    }
}
