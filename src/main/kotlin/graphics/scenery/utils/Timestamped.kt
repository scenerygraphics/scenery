package graphics.scenery.utils

/**
 * Class for objects that need
 */
interface Timestamped {
    /** When the object was created. */
    var created: Long
    /** When the object was last modified. */
    var updated: Long
}
