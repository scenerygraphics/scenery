package graphics.scenery.net

class NetworkObject<T: Networkable>(id:Int, val obj: T,var parents: MutableList<Int>) {
    init {
        obj.networkID = id
    }
    val nID = obj.networkID
    var needsUpdate = false
    var publishedAt = 0L
}
