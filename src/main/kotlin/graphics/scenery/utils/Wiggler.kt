package graphics.scenery.utils

import graphics.scenery.attribute.spatial.HasSpatial
import graphics.scenery.numerics.Random
import graphics.scenery.utils.extensions.plus
import kotlin.concurrent.thread

/**
 * It wiggles a Node.
 */
class Wiggler(val target: HasSpatial, val range: Float = 0.02f, private val lifeTimeMillis: Int? = null) {
    var active = true
        private set
    private var created = System.currentTimeMillis()
    private var thread: Thread? = null

    init {
        val old = target.getAttributeOrNull(Wiggler::class.java)
        if (old?.active == true){
            // if there is an existing Wiggler, just extend its lifetime
            old.created = System.currentTimeMillis()
        } else {
            target.addAttribute(Wiggler::class.java,this)
            thread = thread {
                target.spatial {
                    val originalPosition = position
                    while (active) {
                        position =
                            originalPosition + Random.random3DVectorFromRange(-range, range)

                        if (lifeTimeMillis != null && lifeTimeMillis + created < System.currentTimeMillis()){
                            active = false
                        }

                        Thread.sleep(5)
                    }
                    position = originalPosition
                }
            }
        }
    }

    fun deativate(): Thread? {
        active = false
        return thread
    }
}
