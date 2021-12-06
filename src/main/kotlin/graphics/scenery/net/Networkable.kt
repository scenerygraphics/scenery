package graphics.scenery.net

import java.io.Serializable
import kotlin.reflect.KClass

interface Networkable : Serializable{
    /**
     *  @param getNetworkable Should be used to get other related networkables besides children/parents or attributes
     *  (those are automatically synced) If the desired networkable is not found the call is aborted via an exception.
     *  The exception is caught by the surrounding function and the update will be called again once the desired
     *  networkable has been synced. It might be advisable to get all needed other networkables at the beginning of the
     *  function to not leave the object in an invalid half-update state
     */
    fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable)
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


