package graphics.scenery.utils

import java.util.*

/**
 * Created by ulrik on 1/8/2017.
 */
class RingBuffer<T: Any>(var size: Int) {

    protected var backingStore: ArrayList<T> = ArrayList(size)
    private var currentReadPosition = 0
    private var currentWritePosition = 0

    fun put(element: T) {
        backingStore.set(currentWritePosition % backingStore.size, element)
        currentWritePosition++
    }

    fun get(): T {
        val element = backingStore.get(currentReadPosition % backingStore.size)
        currentReadPosition++

        return element
    }

    fun reset() {
        currentReadPosition = 0
        currentWritePosition = 0
    }
}
