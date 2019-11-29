package graphics.scenery.utils

import graphics.scenery.Hubable

/**
 * Interface for profilers.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
interface Profiler: Hubable {
    /**
     * Profiler sample type.
     */
    enum class SampleType {
        /** Default behaviour, samples will not be merged. */
        Default,
        /** Search parent for samples of the same name and merge the timing instead of adding a new sample. */
        Aggregate,
        /** Merge sample with the parent's if it is the same sample. */
        Recursive
    }

    /**
     * Begins a new sample with [name]. Default type is [SampleType.Default].
     *  Calls to [begin] and [end] behave like brackets an can be nested.
     */
    fun begin(name: String = "", type: SampleType = SampleType.Default)

    /**
     * Ends the current sample. Must have a corresponding [begin].
     */
    fun end()

    /**
     * Sets the current thread name to [name].
     */
    fun setThreadName(name: String)

    /**
     * Closes the profiler instance and cleans up.
     */
    fun close()
}
