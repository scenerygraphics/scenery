package graphics.scenery.utils

import java.util.*

/**
 * Interface for collection of GPU statistics.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
interface GPUStats {
    val utilisations: HashMap<String, Float>

    /**
     * Updates the state of the GPU statistics for a GPU with index [gpuIndex].
     */
    fun update(gpuIndex: Int)

    /**
     * Returns a specific statistic with [name].
     */
    fun get(name: String): Float

    /**
     * Returns the total GPU utilisation as String.
     */
    fun utilisationToString(): String

    /**
     * Returns the total GPU memory utilisation as String.
     */
    fun memoryUtilisationToString(): String
}
