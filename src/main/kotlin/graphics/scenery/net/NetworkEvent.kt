package graphics.scenery.net

sealed class NetworkEvent {
    data class Update(val obj: NetworkWrapper<*>,
                 val additionalData: Any? = obj.obj.getAdditionalUpdateData(),
                 val constructorParameters: Any? = obj.obj.getConstructorParameters()) : NetworkEvent() {
                     constructor(obj: NetworkWrapper<*>):
                         this(obj,obj.obj.getAdditionalUpdateData(),obj.obj.getConstructorParameters())
    }

    data class NewRelation(val parent: Int?, val child: Int) : NetworkEvent()
    object RequestInitialization : NetworkEvent()
}
