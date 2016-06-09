package scenery

import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ulrik on 09/06/2016.
 */
class Hub {
    val elements: ConcurrentHashMap<SceneryElement, Any> = ConcurrentHashMap()

    fun add(type: SceneryElement, obj: Hubable) {
        elements.put(type, obj)

        obj.hub = this
    }

    fun get(type: SceneryElement): Any? {
        return elements.get(type)
    }

    fun has(type: SceneryElement): Boolean {
        return elements.containsKey(type)
    }
}