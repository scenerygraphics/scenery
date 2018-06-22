package graphics.scenery.utils

import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Sticky boolean class, remains true if set to true once.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class StickyBoolean(initial: Boolean) {
    // careful: the lambda is expected to return true to change to the new
    // value, and false to keep the old value
    private var updated: Boolean by Delegates.vetoable(initial) { _, old, new ->
        when {
            // should stay true
            old && new -> true
            !old && new -> true
            old && !new -> false
            !old && !new -> false
            else -> false
        }
    }

    /** Returns the sticky boolean's value */
    operator fun getValue(nothing: Nothing?, property: KProperty<*>): Boolean {
        return updated
    }

    /** Potentially sets booleans value to [b], if not vetoed. */
    operator fun setValue(nothing: Nothing?, property: KProperty<*>, b: Boolean) {
        updated = b
    }
}
