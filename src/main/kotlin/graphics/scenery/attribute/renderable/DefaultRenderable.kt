package graphics.scenery.attribute.renderable

import graphics.scenery.Node
import graphics.scenery.net.Networkable
import java.util.*
import kotlin.collections.HashMap
import kotlin.reflect.KClass

open class DefaultRenderable(override var parent: Node): Renderable, Networkable{
    @Transient override var metadata: HashMap<String, Any> = HashMap()

    private var uuid: UUID = UUID.randomUUID()
    override fun getUuid(): UUID {
        return uuid
    }
    override var isBillboard: Boolean = false

    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable) {
        if (fresh !is DefaultRenderable){
            throw IllegalArgumentException("Got wrong type to update ${this::class.simpleName} ")
        }
        isBillboard = fresh.isBillboard
        uuid = fresh.getUuid()
    }

    override fun lastChange(): Long {
        //TODO("Not yet implemented")
        return Long.MIN_VALUE
    }

    override fun getAttributeClass(): KClass<out Any>? {
        return Renderable::class
    }

    override var networkID: Int = 0

}
