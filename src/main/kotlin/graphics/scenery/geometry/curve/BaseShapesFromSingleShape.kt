package graphics.scenery.geometry.curve

import org.joml.Vector3f

/**
 * Convenience object to create a curve with only a single base shape.
 */
object BaseShapesFromSingleShape {

    /**
     * Returns as many shapes as needed for the curve implementation.
     */
    fun shapes(shape: List<Vector3f>, splinePointsSize: Int): List<List<Vector3f>> {
        val shapeList = ArrayList<List<Vector3f>>(splinePointsSize)
        for (i in 0 until splinePointsSize) {
            shapeList.add(shape)
        }
        return shapeList
    }
}
