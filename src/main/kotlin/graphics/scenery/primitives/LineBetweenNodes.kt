package graphics.scenery.primitives

import graphics.scenery.attribute.spatial.Spatial
import org.joml.Vector3f

/**
 * A line which always goes from one node to another.
 */
class LineBetweenNodes(var from: Spatial, var to: Spatial, transparent: Boolean = false, simple: Boolean = false) :
    Line(capacity = 3, transparent, simple) {

    init {

        update.add {
            if (!visible){
                return@add
            }
            clearPoints()
            addPoint(from.worldPosition())
            addPoint(to.worldPosition())
        }
    }
}

