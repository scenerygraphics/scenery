package graphics.scenery.utils

import org.joml.Vector3f
import kotlin.math.sqrt

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
 */
fun Iterable<Float>.stdDev() : Float {
    val average = this.average()
    return sqrt((this.sumOf { (it - average) * (it - average) } / this.count())).toFloat()
}

/**
 * Perform a Gauss smoothing operation by sliding a kernel with (0.25, 0.5, 0.25) for [iterations] times over the data.
 */
fun gaussSmoothing(samples: List<Float>, iterations: Int): List<Float> {
    var smoothed = samples.toList()
    val kernel = listOf(0.25f, 0.5f, 0.25f)
    for (i in 0 until iterations) {
        val newSmoothed = ArrayList<Float>(smoothed.size)
        // Handle the first element
        newSmoothed.add(smoothed[0] * 0.75f + smoothed[1] * 0.25f)
        // Apply smoothing to the middle elements
        for (j in 1 until smoothed.size - 1) {
            val value = kernel[0] * smoothed[j-1] + kernel[1] * smoothed[j] + kernel[2] * smoothed[j+1]
            newSmoothed.add(value)
        }
        // Handle the last element
        newSmoothed.add(smoothed[smoothed.size - 2] * 0.25f + smoothed[smoothed.size - 1] * 0.75f)

        smoothed = newSmoothed
    }
    return smoothed
}
