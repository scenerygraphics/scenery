package graphics.scenery.volumes

import bdv.tools.brightness.ConverterSetup
import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.net.Networkable
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.times
import org.joml.Vector3f
import java.lang.IllegalArgumentException

class DummyVolume(val counterStart : Int = 0) : DefaultNode("DummyVolume"),HasTransferFunction {
    override var transferFunction: TransferFunction = TransferFunction.flat(0.5f)
        set(m) {
            field = m
            modifiedAt = System.nanoTime()
        }
    var counter = 0

    val converterSetups = ArrayList<ConverterSetup>()
    override var minDisplayRange: Float = 0.0f
        get() = field
        set(value) {
            setTransferFunctionRange(value, maxDisplayRange)
            field = value
        }
    override var maxDisplayRange: Float = 65535F
        get() = field
        set(value) {
            setTransferFunctionRange(minDisplayRange, value)
            field = value
        }
    var colormap: Colormap = Colormap.get("viridis")
        set(m) {
            field = m
            modifiedAt = System.nanoTime()
        }
    init {
        name = "DummyVolume"
        counter = counterStart
    }

    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
        if (fresh !is DummyVolume) throw IllegalArgumentException("Update called with object of foreign class")
        super.update(fresh, getNetworkable, additionalData)
        this.transferFunction = fresh.transferFunction
        this.minDisplayRange = fresh.minDisplayRange
        this.maxDisplayRange = fresh.maxDisplayRange
        this.colormap = fresh.colormap
        this.counter = fresh.counter
    }

    @JvmOverloads
    open fun setTransferFunctionRange(min: Float, max: Float, forSetupId: Int = 0) {
        converterSetups.getOrNull(forSetupId)?.setDisplayRange(min.toDouble(), max.toDouble())
    }
    override fun getConstructorParameters(): Any? {
        return counterStart
    }

    override fun constructWithParameters(parameters: Any, hub: Hub): Networkable {
        val counterStart = parameters as Int
        return DummyVolume(counterStart)
    }
}
