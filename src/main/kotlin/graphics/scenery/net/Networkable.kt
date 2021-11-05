package graphics.scenery.net

import java.io.Serializable
import kotlin.reflect.KClass

interface Networkable : Serializable{
    fun update(fresh: Networkable)
    fun getSubcomponents(): List<Networkable> = emptyList()
    fun hasChanged(): Boolean
    // only set for attributes
    fun getAttributeClass(): KClass<out Any>?

    //set by external
    var networkID: Int
}


