package graphics.scenery.net

class NetworkWrapper<T: Networkable>(id:Int,
                                     val obj: T,
                                     var parents: MutableList<Int>,
                                     var publishedAt: Long = System.nanoTime()) {
    init {
        obj.networkID = id
    }

    var additionalData: Any? = null

    /**
     * Convenience accessor
     */
    val networkID
        get() = obj.networkID
}
