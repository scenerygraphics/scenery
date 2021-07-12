package graphics.scenery.utils

import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.numerics.Random
import graphics.scenery.utils.extensions.plus
import kotlin.concurrent.thread

/**
 * It wiggles a [Node].
 */
class Wiggler(val target: Spatial, val range: Float = 0.02f) {
    private var active = true

    init {
        thread {
            val originalPosition = target.position
            while (active) {
                target.position =
                    originalPosition + Random.random3DVectorFromRange(-range, range)
                Thread.sleep(5)
            }
            target.position = originalPosition
        }
    }

    fun deativate() {
        active = false
    }
}
