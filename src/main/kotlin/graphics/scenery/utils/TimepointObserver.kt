package graphics.scenery.utils

/**
 * Interface to allow subscription to timepoint updates. This interface should be inherited from classes that want to
 * listen to a [TimepointObservable].
 */
interface TimepointObserver {

    /**
     * Called when the timepoint was updated.
     */
    fun onTimePointChanged(timepoint: Int)
}

/**
 * A class that can trigger timepoint changes and allows notifying a list of [TimepointObserver]s about it.
 * */
open class TimepointObservable {
    private val observers = mutableListOf<TimepointObserver>()

    fun registerObserver(observer: TimepointObserver) {
        observers.add(observer)
    }

    fun unregisterObserver(observer: TimepointObserver) {
        observers.remove(observer)
    }

    fun notifyObservers(timepoint: Int) {
        observers.forEach { it.onTimePointChanged(timepoint) }
    }
}
