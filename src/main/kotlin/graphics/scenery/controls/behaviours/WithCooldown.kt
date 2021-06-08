package graphics.scenery.controls.behaviours

import org.scijava.ui.behaviour.ClickBehaviour
import java.util.*
import kotlin.concurrent.schedule
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Class to enable e.g. [ClickBehaviour]s to be activated once, and then
 * only again after a [cooldown] given as [Duration]. Can e.g. be used
 * in [ClickBehaviour.click]. The time [block] will be executed again after
 * is stored with respect to the object [obj] given.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
@OptIn(ExperimentalTime::class)
open class WithCooldown(val obj: Any, cooldown: Duration, block: () -> Unit) {
    companion object {
        protected val invocations = HashSet<Any>()
    }

    init {
        val invoked = invocations.contains(obj)
        if (!invoked) {
            block.invoke()
            invocations.add(obj)
            Timer(true).schedule(cooldown.toLongMilliseconds()) { invocations.remove(obj) }
        }
    }
}

/**
 * Invokes this [ClickBehaviour] once, and only again after the given
 * [cooldown] period has passed. Use in [ClickBehaviour.click].
 */
@OptIn(ExperimentalTime::class)
fun ClickBehaviour.withCooldown(cooldown: Duration, block: () -> Unit) {
    WithCooldown(this, cooldown, block)
}
