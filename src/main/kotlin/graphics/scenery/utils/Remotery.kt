package graphics.scenery.utils

import graphics.scenery.Hub
import graphics.scenery.Hubable
import org.lwjgl.PointerBuffer
import org.lwjgl.util.remotery.Remotery.RMTSF_Aggregate
import org.lwjgl.util.remotery.Remotery.RMTSF_None
import org.lwjgl.util.remotery.Remotery.RMTSF_Recursive
import org.lwjgl.util.remotery.Remotery.rmt_BeginCPUSample
import org.lwjgl.util.remotery.Remotery.rmt_CreateGlobalInstance
import org.lwjgl.util.remotery.Remotery.rmt_DestroyGlobalInstance
import org.lwjgl.util.remotery.Remotery.rmt_EndCPUSample
import org.lwjgl.util.remotery.Remotery.rmt_SetCurrentThreadName

/**
 * Class for using the Remotery profiler.
 * To use, set `scenery.Profiler` to true, and connect to the profiler
 * by using opening `vis/index.html` from the [Remotery Github repository](https://github.com/Celtoys/Remotery).
 *
 * As parameter, the class requires a [hub] to attach to.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class Remotery(override var hub : Hub?) : Hubable, AutoCloseable {
    private var instance = PointerBuffer.allocateDirect(1)
    private val logger by LazyLogger()

    /**
     * Remotery sample type.
     */
    enum class SampleType(val t: Int) {
        /** Default behaviour, samples will not be merged. */
        Default(RMTSF_None),
        /** Search parent for samples of the same name and merge the timing instead of adding a new sample. */
        Aggregate(RMTSF_Aggregate),
        /** Merge sample with the parent's if it is the same sample. */
        Recursive(RMTSF_Recursive)
    }

    init {
        val error = rmt_CreateGlobalInstance(instance)
        if(error == 0) {
            logger.info("Created Remotery profiler instance")
        } else {
            throw IllegalStateException("Could not create Remotery profiler ($error)")
        }
    }

    /**
     * Sets the current thread name to [name].
     */
    fun setThreadName(name: String) {
        rmt_SetCurrentThreadName(name)
    }

    /**
     * Begins a new sample with [name]. Default type is [SampleType.Default].
     *  Calls to [begin] and [end] behave like brackets an can be nested.
     */
    fun begin(name: String = "", type: SampleType = SampleType.Default) {
        rmt_BeginCPUSample(name, type.ordinal, null)
    }

    /**
     * Ends the current sample. Must have a corresponding [begin].
     */
    fun end() {
        rmt_EndCPUSample()
    }

    /**
     * Closes the Remotery instance.
     */
    override fun close() {
        rmt_DestroyGlobalInstance(instance.get(0))
        logger.info("Closing Remotery...")
    }
}
