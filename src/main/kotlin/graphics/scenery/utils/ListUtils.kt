package graphics.scenery.utils

import org.joml.Vector3f
import kotlin.math.sqrt

/**
 * Convert a 3-element [DoubleArray] to a [Vector3f].
 * */
fun DoubleArray.toVector3f(): Vector3f {
    require(size == 3) { "DoubleArray must have exactly 3 elements" }
    return Vector3f(this[0].toFloat(), this[1].toFloat(), this[2].toFloat())
}

/**
 * From a [list] of Floats, return both the index of local maxima, and their value,
 * packaged nicely as a Pair<Int, Float>
 */
fun localMaxima(list: List<Float>): List<Pair<Int, Float>> {
    return list.windowed(3, 1).mapIndexed { index, l ->
        val left = l[0]
        val center = l[1]
        val right = l[2]

        // we have a match at center
        if (left < center && center > right) {
            index * 1 + 1 to center
        } else {
            null
        }
    }.filterNotNull()
}

/**
 * Returns the standard deviation of the elements in a [Float] collection.
 * */
fun Iterable<Float>.stdDev() : Float {
    val average = this.average()
    return sqrt((this.sumOf { (it - average) * (it - average) } / this.count())).toFloat()
}
