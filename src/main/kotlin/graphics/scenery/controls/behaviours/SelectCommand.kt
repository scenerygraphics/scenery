package graphics.scenery.controls.behaviours

import cleargl.GLVector
import graphics.scenery.*
import org.scijava.ui.behaviour.ClickBehaviour
import graphics.scenery.backends.Renderer
import org.slf4j.LoggerFactory

/**
 * Raycasting-based selection command
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class SelectCommand(private val name: String,
                         private val renderer: Renderer,
                         private val scene: Scene,
                         private val cam: Camera,
                         private var debugRaycast: Boolean = false,
                         private var action: ((List<SelectResult>) -> Any) = {}) : ClickBehaviour {
    val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    data class SelectResult(val node: Node, val distance: Float)

    override fun click(x: Int, y: Int) {
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

        if(debugRaycast) {
            val indicatorMaterial = Material()
            indicatorMaterial.diffuse = GLVector(1.0f, 0.2f, 0.2f)
            indicatorMaterial.specular = GLVector(1.0f, 0.2f, 0.2f)
            indicatorMaterial.ambient = GLVector(0.0f, 0.0f, 0.0f)

            (5..50).forEach {
                val s = Sphere(0.2f, 20)
                s.material = indicatorMaterial
                s.position = worldPos + worldDir * it.toFloat() * 5.0f
                scene.addChild(s)
            }
        }

        val matches = scene.discover(scene, { node ->
            node is Renderable && node.visible
        }).map {
            Pair(it, intersectAABB(it, worldPos, worldDir))
        }.filter {
            it.second.first && it.second.second > 0.0f
        }.map {
            SelectResult(it.first, it.second.second)
        }.sortedBy {
            it.distance
        }

        if(debugRaycast) {
            logger.info(matches.map { "${it.node.name} at distance ${it.distance}" }.joinToString(", "))

            val m = Material()
            m.diffuse = GLVector(1.0f, 0.0f, 0.0f)
            m.specular = GLVector(0.0f, 0.0f, 0.0f)
            m.ambient = GLVector(0.0f, 0.0f, 0.0f)
            m.needsTextureReload = true

            matches.firstOrNull()?.let {
                it.node.material = m
                it.node.initialized = false
                it.node.metadata.clear()
                it.node.dirty = true
            }
        }

        action.invoke(matches)
    }

    // code adapted from zachamarz, http://gamedev.stackexchange.com/a/18459
    fun intersectAABB(node: Node, origin: GLVector, dir: GLVector): Pair<Boolean, Float> {
        if (node.boundingBoxCoords == null) {
            return false.to(Float.MAX_VALUE)
        } else {
            val bbmin = GLVector(node.boundingBoxCoords!![0], node.boundingBoxCoords!![2], node.boundingBoxCoords!![4], 1.0f)
            val bbmax = GLVector(node.boundingBoxCoords!![1], node.boundingBoxCoords!![3], node.boundingBoxCoords!![5], 1.0f)

            val min = node.world.mult(bbmin)
            val max = node.world.mult(bbmax)

            // skip if inside the bounding box
            if(origin.x() > min.x() && origin.x() < max.x()
                && origin.y() > min.y() && origin.y() < max.y()
                && origin.z() > min.z() && origin.z() < max.z()) {
                return false.to(0.0f)
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
                return false.to(tmax)
            }

            // we have missed the AABB
            if (tmin > tmax) {
                return false.to(tmax)
            }

            // we have a match!
            return true.to(tmin)
        }
    }
}
