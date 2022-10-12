package graphics.scenery.net

import graphics.scenery.Hub
import java.io.Serializable
import kotlin.reflect.KClass

/**
 * An object that can be synchronised over the network.
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
interface Networkable : Serializable {
    /**
     *  Mind maybe calling [super.update] here.
     *
     *  @param getNetworkable Should be used to get other related networkables besides children/parents or attributes
     *  (those are automatically synced). If the desired networkable is not found the call is aborted via an exception.
     *  The exception is caught by the surrounding function and the update will be called again once the desired
     *  networkable has been synced. It might be advisable to get all needed other networkables at the beginning of the
     *  function before applying the new values to not leave the object in an invalid half-update state.
     *
     *  Don't access members of the @return networkable which are also networkables themself. Second level networkable
     *  relations might be wrong. Instead, handle them as direct relations of this object.
     *
     *  @param fresh by-value deep (except for transient members) copy of the original at the server
     */
    fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?)

    /**
     * Serialize data which is not picket up by kryo eg. transient members
     * Attention: [super] might also have data.
     */
    fun getAdditionalUpdateData(): Any? = null

    fun constructWithParameters(parameters: Any, hub: Hub) = this

    fun getConstructorParameters(): Any? = null

    /**
     * Time point of last modification in [System.nanoTime()]
     */
    var modifiedAt: Long

    /**
     * Get subcomponents which are not part of the node graph eg. attributes
     */
    fun getSubcomponents(): List<Networkable> = emptyList()

    // only set for attributes
    fun getAttributeClass(): KClass<out Any>? = null

    fun wantsSync(): Boolean = true

    //set by framework or if <-1 it is used to register existing object in client and server scenes
    var networkID: Int

    fun updateModifiedAt() {
        modifiedAt = System.nanoTime()
    }
}


