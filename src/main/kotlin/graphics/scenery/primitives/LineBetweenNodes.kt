package graphics.scenery.primitives

import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.utils.extensions.minus
import org.joml.Vector3f

/**
 * A line which always goes from one node to another.
 */
class LineBetweenNodes(var from: Spatial, var to: Spatial, transparent: Boolean = false, simple: Boolean = false) :
    Line(capacity = 3, transparent, simple) {

    init {

        addPoint(Vector3f())
        addPoint(Vector3f(0f,1f,0f,))

        update.add {
            if (!visible) {
                return@add
            }
            spatial() {
                val p1 = from.worldPosition(Vector3f())
                val p2 = to.worldPosition(Vector3f())
                orientBetweenPoints(p1, p2)
                scale = Vector3f(p1.distance(p2)) //todo: times the inverse of world
                position = p1 - (parent?.spatialOrNull()?.worldPosition() ?: Vector3f())
            }
        }
    }
}

