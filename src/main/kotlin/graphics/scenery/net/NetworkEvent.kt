package graphics.scenery.net

sealed class NetworkEvent() {
    class NewObject(val obj: NetworkObject<*>): NetworkEvent()
    class NewRelation(val parent: Int, val Child:Int): NetworkEvent()
}
