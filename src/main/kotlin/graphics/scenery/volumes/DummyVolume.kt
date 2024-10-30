package graphics.scenery.volumes

import bdv.tools.brightness.ConverterSetup
import graphics.scenery.*
import graphics.scenery.net.Networkable
import java.lang.IllegalArgumentException

/**
 * A container for the primary user parameters used in volume rendering. Can be used in server-side
 * remote volume rendering applications to synchronize volume rendering parameters without transferring
 * volume data between server and client.
 */
class DummyVolume(val counterStart : Int = 0) : DefaultNode("DummyVolume"), HasTransferFunction {

    /** The transfer function to use for the volume. Flat by default. */
    override var transferFunction: TransferFunction = TransferFunction.flat(0.5f)
        set(m) {
            field = m
            modifiedAt = System.nanoTime()
        }
    var counter = 0

    val converterSetups = ArrayList<ConverterSetup>()

    /** Minimum display range. */
    override var minDisplayRange: Float = 0.0f
        get() = field
        set(value) {
            setTransferFunctionRange(value, maxDisplayRange)
            field = value
            modifiedAt = System.nanoTime()
        }

    /** Maximum display range. */
    override var maxDisplayRange: Float = 65535f
        get() = field
        set(value) {
            setTransferFunctionRange(minDisplayRange, value)
            field = value
            modifiedAt = System.nanoTime()
        }

    /** A pair containing the min and max display range limits. */
    override var displayRangeLimits: Pair<Float, Float> = Pair<Float, Float>(minDisplayRange,maxDisplayRange)
        get() = field
        set(m) {
            field = m
            modifiedAt = System.nanoTime()
        }

    /** The color map for the volume. */
    var colormap: Colormap = Colormap.get("viridis")
        set(m) {
            field = m
            modifiedAt = System.nanoTime()
        }

    init {
        name = "DummyVolume"
        counter = counterStart
    }

    /**
     * Update the DummyVolume with the [fresh] one received over the network.
     */
    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
        if (fresh !is DummyVolume) throw IllegalArgumentException("Update called with object of foreign class")
        super.update(fresh, getNetworkable, additionalData)
        this.transferFunction = fresh.transferFunction
        this.minDisplayRange = fresh.minDisplayRange
        this.maxDisplayRange = fresh.maxDisplayRange
        this.colormap = fresh.colormap
        this.counter = fresh.counter
    }

    /**
     * Resets the range of this volume's transfer function to [min] and [max] for the setup given as [forSetupId].
     */
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
