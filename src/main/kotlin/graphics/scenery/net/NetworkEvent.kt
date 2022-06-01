package graphics.scenery.net

/**
 * Events Client and Server send each other to facilitate syncing.
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
sealed class NetworkEvent {
    /**
     * An object has benn created or properties have been changed.
     */
    data class Update(val obj: NetworkWrapper<*>,
                 val additionalData: Any? = obj.obj.getAdditionalUpdateData(),
                 val constructorParameters: Any? = obj.obj.getConstructorParameters()) : NetworkEvent() {
                     constructor(obj: NetworkWrapper<*>):
                         this(obj,obj.obj.getAdditionalUpdateData(),obj.obj.getConstructorParameters())
    }

    /**
     * A scene graph parent-child relation has been changed.
     */
    data class NewRelation(val parent: Int?, val child: Int) : NetworkEvent()

    /**
     * A client requests a full send of the scene.
     */
    object RequestInitialization : NetworkEvent()
}
