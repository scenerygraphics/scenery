package graphics.scenery.net

sealed class NetworkEvent() {
    class Update(val obj:  NetworkObject<*>): NetworkEvent()
    class NewRelation(val parent: Int, val Child:Int): NetworkEvent()
    class RequestInitialization(): NetworkEvent()
}
