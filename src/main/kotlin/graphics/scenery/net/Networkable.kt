package graphics.scenery.net

import java.io.Serializable
import kotlin.reflect.KClass

interface Networkable : Serializable {
    /**
     *  @param getNetworkable Should be used to get other related networkables besides children/parents or attributes
     *  (those are automatically synced). If the desired networkable is not found the call is aborted via an exception.
     *  The exception is caught by the surrounding function and the update will be called again once the desired
     *  networkable has been synced. It might be advisable to get all needed other networkables at the beginning of the
     *  function to not leave the object in an invalid half-update state.
     *
     *  Don't access members of the @return networkable which are also networkables themself. 2.level networkable
     *  relations might be wrong. Instead add them to the direct relations of this object.
     *
     *  @param fresh by-value deep (except for transient members) copy of the original at the server
     */
    fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?)

    /**
     * Serialize data which is not picket up by kryo eg. transient members
     */
    fun getAdditionalData(): Any? = null

    /**
     * Time point of last modification in [System.nanoTime()]
     */
    var modifiedAt: Long

    /**
     * Get sub components which are not part of the node graph eg. attributes
     */
    fun getSubcomponents(): List<Networkable> = emptyList()

    // only set for attributes
    fun getAttributeClass(): KClass<out Any>?

    fun wantsSync(): Boolean = true

    //set by external
    var networkID: Int

    fun updateModifiedAt() {
        modifiedAt = System.nanoTime()
    }
}


