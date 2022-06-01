package graphics.scenery.net

/**
 * A wrapper for [Networkable]s to enrich them with local information
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
class NetworkWrapper<T: Networkable>(id:Int,
                                     val obj: T,
                                     var parents: MutableList<Int>,
                                     var publishedAt: Long = System.nanoTime()) {
    init {
        obj.networkID = id
    }

    /**
     * Convenience accessor
     */
    val networkID
        get() = obj.networkID
}
