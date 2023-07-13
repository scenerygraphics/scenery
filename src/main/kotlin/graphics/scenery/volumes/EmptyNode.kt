package graphics.scenery.volumes

import graphics.scenery.DefaultNode
import graphics.scenery.Light
import graphics.scenery.net.Networkable

class EmptyNode : DefaultNode("EmptyNode"),Networkable {

    var value : String = ""
        set(value){
            field = value
            modifiedAt = System.nanoTime()
        }

    init {
        name = "EmptyNode"
    }

    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
        super.update(fresh, getNetworkable,additionalData)
        if (fresh !is EmptyNode) throw IllegalArgumentException("Update called with object of foreign class")
        this.value = fresh.value

    }
}
