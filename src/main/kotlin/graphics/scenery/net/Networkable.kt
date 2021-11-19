package graphics.scenery.net

import java.io.Serializable
import kotlin.reflect.KClass

interface Networkable : Serializable{
    fun update(fresh: Networkable)
    fun getSubcomponents(): List<Networkable> = emptyList()

    /**
     * Time point of last change in [System.nanoTime()]
     */
    fun lastChange(): Long

    // only set for attributes
    fun getAttributeClass(): KClass<out Any>?
    fun wantsSync(): Boolean = true

    //set by external
    var networkID: Int
}


