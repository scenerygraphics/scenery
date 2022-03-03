package graphics.scenery.volumes

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.net.Networkable
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.times
import org.joml.Vector3f
import java.lang.IllegalArgumentException

open class DummyVolume(val counterStart : Int = 0) : DefaultNode("DummyVolume") {
    var transferFunction: TransferFunction = TransferFunction.flat(0.5f)
    set(m) {
        field = m
        modifiedAt = System.nanoTime()
    }
    var counter = 0

    init {
        name = "DummyVolume"
        counter = counterStart
    }

    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
        if (fresh !is DummyVolume) throw IllegalArgumentException("Update called with object of foreign class")
        super.update(fresh, getNetworkable, additionalData)
        this.transferFunction = fresh.transferFunction
        this.counter = fresh.counter
    }

    override fun getConstructorParameters(): Any? {
        return counterStart
    }

    override fun constructWithParameters(parameters: Any, hub: Hub): Networkable {
        val counterStart = parameters as Int
        return DummyVolume(counterStart)
    }
}
