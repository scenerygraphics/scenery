package graphics.scenery.controls.behaviours

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.LazyLogger
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.reflect.KProperty

/**
 * Raycasting-based selection command
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class SelectCommand @JvmOverloads constructor(private val name: String,
                                                   private val renderer: Renderer,
                                                   private val scene: Scene,
                                                   private val camera: () -> Camera?,
                                                   private var debugRaycast: Boolean = false,
                                                   private var action: ((List<SelectResult>) -> Unit) = {}) : ClickBehaviour {
    protected val logger by LazyLogger()

    val cam: Camera? by CameraDelegate()

    inner class CameraDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Camera? {
            return camera.invoke()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Camera?) {
            throw UnsupportedOperationException()
        }
    }

    data class SelectResult(val node: Node, val distance: Float)

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
                node.visible && node !is BoundingGrid
            }).map {
                Pair(it, intersectAABB(it, worldPos, worldDir))
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

    // code adapted from zachamarz, http://gamedev.stackexchange.com/a/18459
    fun intersectAABB(node: Node, origin: GLVector, dir: GLVector): Pair<Boolean, Float> {
            val bbmin = node.getMaximumBoundingBox().min.xyzw()
            val bbmax = node.getMaximumBoundingBox().max.xyzw()

            val min = node.world.mult(bbmin)
            val max = node.world.mult(bbmax)

            // skip if inside the bounding box
            if(origin.x() > min.x() && origin.x() < max.x()
                && origin.y() > min.y() && origin.y() < max.y()
                && origin.z() > min.z() && origin.z() < max.z()) {
                return false to 0.0f
            }

            val invDir = GLVector(1 / dir.x(), 1 / dir.y(), 1 / dir.z())

            val t1 = (min.x() - origin.x()) * invDir.x()
            val t2 = (max.x() - origin.x()) * invDir.x()
            val t3 = (min.y() - origin.y()) * invDir.y()
            val t4 = (max.y() - origin.y()) * invDir.y()
            val t5 = (min.z() - origin.z()) * invDir.z()
            val t6 = (max.z() - origin.z()) * invDir.z()

            val tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6))
            val tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6))

            // we are in front of the AABB
            if (tmax < 0) {
                return false to tmax
            }

            // we have missed the AABB
            if (tmin > tmax) {
                return false to tmax
            }

            // we have a match!
            return true to tmin
    }
}
