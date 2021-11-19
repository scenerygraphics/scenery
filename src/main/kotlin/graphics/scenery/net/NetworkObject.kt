package graphics.scenery.net

class NetworkObject<T: Networkable>(id:Int,
                                    val obj: T,
                                    var parents: MutableList<Int>,
                                    var publishedAt: Long = System.nanoTime()) {
    init {
        obj.networkID = id
    }

    /**
     * Convenience accessor
     */
    val networkID = obj.networkID
}
