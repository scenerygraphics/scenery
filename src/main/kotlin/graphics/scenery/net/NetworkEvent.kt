package graphics.scenery.net

sealed class NetworkEvent() {
    class Update(val obj:  NetworkWrapper<*>): NetworkEvent()
    class NewRelation(val parent: Int?, val child:Int): NetworkEvent()
    class RequestInitialization(): NetworkEvent()
}
