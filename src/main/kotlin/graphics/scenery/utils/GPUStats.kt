package graphics.scenery.utils

import java.util.*

/**
 * Created by ulrik on 2/1/2017.
 */
interface GPUStats {
    val utilisations: HashMap<String, Float>
    fun update(gpuIndex: Int)
    fun get(name: String): Float
    fun utilisationToString(): String
    fun memoryUtilisationToString(): String
}
