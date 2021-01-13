package graphics.scenery.utils

/**
 * Class to wrap objects that might need to be stored in a [TimestampedConcurrentHashMap].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class SimpleTimestamped<T>(val contents: T) : Timestamped {
    /** The object's creation timestamp. */
    override var created: Long = System.nanoTime()
    /** The object's updated timestamp. */
    override var updated: Long = System.nanoTime()
}
