package graphics.scenery.utils

/**
 * Interface to allow subscription to timepoint updates. This interface should be inherited from classes that want to
 * listen to a [TimepointObservable].
 */
interface TimepointObserver {

    /** Called when the timepoint was updated. */
    fun onTimePointChanged(timepoint: Int)
}

/**
 * A class that can trigger timepoint changes and allows notifying a list of [TimepointObserver]s about it
 * via [notifyObservers].
 * */
open class TimepointObservable {
    private val observers = mutableListOf<TimepointObserver>()

    /** Adds a new [TimepointObserver] to the list of observers. The [TimepointObserver.onTimePointChanged] method is
     *  called when [notifyObservers] is triggered. */
    fun registerObserver(observer: TimepointObserver) {
        observers.add(observer)
    }

    /** Remove a [TimepointObserver] from this observable. */
    fun unregisterObserver(observer: TimepointObserver) {
        observers.remove(observer)
    }

    /** Call [TimepointObserver.onTimePointChanged] for each registered observer. */
    fun notifyObservers(timepoint: Int) {
        observers.forEach { it.onTimePointChanged(timepoint) }
    }
}
