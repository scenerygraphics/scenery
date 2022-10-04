package graphics.scenery.utils

import java.util.concurrent.ConcurrentHashMap

class TimestampedConcurrentHashMap<K : Any, V: Timestamped>(initialCapacity: Int = 10) : ConcurrentHashMap<K, V>(initialCapacity) {
    private var added = ConcurrentHashMap<K, Long>()

    override fun put(key: K, value: V): V? {
        added[key] = System.nanoTime()
        return super.put(key, value)
    }

    override fun remove(key: K): V? {
        val before = super.remove(key)
        if(before != null) {
            added.remove(key)
        }

        return before
    }

    fun hasChanged(key: K, value: V, now: Long = System.nanoTime()): Boolean {
        return added.getOrDefault(key, now) >= now || value.updated >= now || value.created >= now
    }

    inline fun forEachChanged(now: Long = System.nanoTime(), action: (Map.Entry<K, V>) -> Unit) {
        for (element in this) {
            if(hasChanged(element.key, element.value, now)) {
                action(element)
            }
        }
    }

    inline fun <R> mapChanged(now: Long = System.nanoTime(), transform: (Map.Entry<K, V>) -> R): List<R> {
        return filter { this.hasChanged(it.key, it.value, now)}
            .mapTo(ArrayList<R>(size), transform)
    }
}
