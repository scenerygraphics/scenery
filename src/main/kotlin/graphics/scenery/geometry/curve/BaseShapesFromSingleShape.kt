package graphics.scenery.geometry.curve

import org.joml.Vector3f

object BaseShapesFromSingleShape {
    fun shapes(shape: List<Vector3f>, splinePointsSize: Int): List<List<Vector3f>> {
        val shapeList = ArrayList<List<Vector3f>>(splinePointsSize)
        for (i in 0 until splinePointsSize) {
            shapeList.add(shape)
        }
        return shapeList
    }
}
