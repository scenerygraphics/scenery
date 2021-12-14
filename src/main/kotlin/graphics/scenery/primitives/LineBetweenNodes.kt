package graphics.scenery.primitives

import graphics.scenery.attribute.spatial.Spatial
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
            if (!visible){
                return@add
            }
            spatial(){
                orientBetweenPoints(from.worldPosition(),to.worldPosition())
                scale = Vector3f(from.worldPosition().distance(to.worldPosition()))
                position = from.worldPosition()
            }
        }
    }
}

