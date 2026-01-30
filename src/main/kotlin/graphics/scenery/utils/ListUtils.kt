package graphics.scenery.utils

import org.joml.Vector3f

fun DoubleArray.toVector3f(): Vector3f {
    require(size == 3) { "DoubleArray must have exactly 3 elements" }
    return Vector3f(this[0].toFloat(), this[1].toFloat(), this[2].toFloat())
}
