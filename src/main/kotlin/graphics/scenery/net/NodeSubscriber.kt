package graphics.scenery.net

import com.esotericsoftware.kryo.io.Input
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.utils.lazyLogger
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.ByteArrayInputStream
import java.util.concurrent.LinkedBlockingQueue


/**
 * Client of scenery networking.
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
class NodeSubscriber(
    override var hub: Hub?,
    ip: String = "tcp://localhost",
    portPublish: Int = 7777,
    portBackchannel: Int = 6666,
    val context: ZContext,
    startNetworkActivity: Boolean = true //disables network stuff for testing
) : Agent(), Hubable {
    private val logger by lazyLogger()
    val kryo = NodePublisher.freeze()

    private val addressSubscribe = "$ip:$portPublish"
    private val addressBackchannel = "$ip:$portBackchannel"
    var subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
    var backchannel: ZMQ.Socket = context.createSocket(SocketType.PUB)

    private val networkObjects = hashMapOf<Int, NetworkWrapper<*>>()

    /** This is the hand-of point between the network thread and update/main-loop thread */
    private val eventQueue = LinkedBlockingQueue<NetworkEvent>()
    private val waitingOnNetworkable = mutableMapOf<Int, List<Pair<NetworkEvent, WaitReason>>>()

    init {
        if (startNetworkActivity) {
            if (subscriber.connect(addressSubscribe)) {
                logger.info("Client connected to main channel at $addressSubscribe")
            }
            subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)
            subscriber.receiveTimeOut = 100
            if (backchannel.connect(addressBackchannel)) {
                logger.info("Client connected to back channel at $addressBackchannel")
            }
            startAgent()
            NodePublisher.sendEvent(NetworkEvent.RequestInitialization, kryo, backchannel, logger)
        }
    }

    override fun onLoop() {
        try {
            val payload: ByteArray = subscriber.recv() ?: return

            val bin = ByteArrayInputStream(payload)
            val input = Input(bin)
            val event = kryo.readClassAndObject(input) as? NetworkEvent
                ?: throw IllegalStateException("Received unknown, not NetworkEvent payload")
            eventQueue.add(event)
        } catch (t: ZMQException) {
            if (t.errorCode != 4) {//Errno 4 : Interrupted function
                // Interrupted exceptions are expected when closing the Subscriber and no need to worry
                throw t
            }
        } catch (t: com.esotericsoftware.kryo.KryoException){
            logger.warn("Ignoring Kryo Exception:",t)
        }
    }

    /**
     * Used in Unit test
     */
    @Suppress("unused")
    private fun debugListen(event: NetworkEvent) {
        eventQueue.add(event)
    }

    class NetworkableNotFoundException(val id: Int) : IllegalStateException()

    private fun getNetworkable(id: Int): Networkable {
        return networkObjects[id]?.obj ?: throw NetworkableNotFoundException(id)
    }

    /**
     * Should be called in update life cycle
     */
    fun networkUpdate(scene: Scene) {
        while (!eventQueue.isEmpty()) {
            when (val event = eventQueue.poll()) {
                is NetworkEvent.Update -> {
                    processUpdateEvent(event, scene)
                }
                is NetworkEvent.NewRelation -> {
                    processNewRelationEvent(event)
                }
                is NetworkEvent.RequestInitialization -> {
                    logger.error("received ${event::class.java.simpleName} and it should not arrive at the subscriber.")
                }
                else -> {
                    logger.error("received ${event::class.java.simpleName} and dont know what to do with it.")
                }
            }
        }
    }

    private fun processNewRelationEvent(event: NetworkEvent.NewRelation) {
        val parent = event.parent?.let { networkObjects[it] }?.obj as? Node
        if (event.parent != null && parent == null) {
            waitingOnNetworkable.getOrDefault(event.parent, listOf()) + (event to WaitReason.UpdateRelation)
            return
        }
        val childWrapper = networkObjects[event.child]
        if (childWrapper == null) {
            waitingOnNetworkable.getOrDefault(event.child, listOf()) + (event to WaitReason.UpdateRelation)
            return
        }
        when (val child = childWrapper.obj) {
            is Node -> {
                if (parent == null) {
                    child.parent?.removeChild(child)
                } else {
                    parent.addChild(child)
                }
            }
            else -> {
                // Attribute
                parent?.addAttributeFromNetwork(child.getAttributeClass()!!.java, child)
            }
        }
    }

    private fun processUpdateEvent(event: NetworkEvent.Update, scene: Scene) {
        val networkWrapper = event.wrapper

        // ---------- update -------------
        // The object exists on this client -> we only need to update it
        networkObjects[networkWrapper.networkID]?.let {
            val fresh = networkWrapper.obj
            val tmp = it.obj
            try {
                tmp.update(fresh, this::getNetworkable, event.additionalData)
            } catch (e: NetworkableNotFoundException) {
                waitingOnNetworkable[e.id] =
                    waitingOnNetworkable.getOrDefault(e.id, listOf()) + (event to WaitReason.UpdateRelation)
            }
            return
        }

        // ------------ new object ----------
        // The object does not exist on the client -> we need to add it
        var networkable = networkWrapper.obj
        when (networkable) {
            is Scene -> {
                // dont use the scene from network, but adapt own scene
                scene.networkID = networkable.networkID
                try {
                    scene.update(networkable, this::getNetworkable, event.additionalData)
                } catch (e: NetworkableNotFoundException) {
                    logger.warn("Waiting on related Network Object in scene update. This is likely an invalid, irremediable state.")
                    waitingOnNetworkable[e.id] =
                        waitingOnNetworkable.getOrDefault(e.id, listOf()) + (event to WaitReason.Parent)
                }
                networkObjects[networkWrapper.networkID] =
                    NetworkWrapper(networkWrapper.networkID, scene, mutableListOf())
                networkable = scene
            }
            is Node -> {
                val parentId = networkWrapper.parents.first() // nodes have only one parent
                val parent = networkObjects[parentId]?.obj as? Node
                if (parent == null) {
                    waitingOnNetworkable[parentId] =
                        waitingOnNetworkable.getOrDefault(parentId, listOf()) + (event to WaitReason.Parent)
                    return
                }

                val newNode = if (networkable.networkID < -1) {
                    scene.discover(scene,{ (it as? Networkable)?.networkID == networkable.networkID}).firstOrNull()
                        ?: throw IllegalStateException("${networkable::class.simpleName} with preset " +
                            "network id ${networkable.networkID} is missing in scene")
                } else
                    event.constructorParameters?.let { networkable.constructWithParameters(it, hub!!) as Node
                    }?: networkable


                val newWrapped = NetworkWrapper(
                    networkWrapper.networkID,
                    newNode,
                    networkWrapper.parents,
                    networkWrapper.publishedAt
                )

                networkObjects[networkWrapper.networkID] = newWrapped
                // update relations (and other values if created client site with constructor params)
                processUpdateEvent(event, scene)

                parent.addChild(newNode)
                networkable = newNode
            }
            else -> {
                // It is an attribute
                val attributeBaseClass = networkable.getAttributeClass()
                    ?: throw IllegalStateException(
                        "Received unknown object from server. ${networkable.javaClass.simpleName} " +
                            "Maybe an attribute missing a getAttributeClass implementation?"
                    )

                val newAttribute = if (networkable.networkID < -1) {
                    // look for a node which has an attribute with the desired network ID
                    val parent = scene.discover(scene, {
                        (it as? Networkable)?.getSubcomponents()
                            ?.any { it.networkID == networkable.networkID } ?: false
                    }).firstOrNull() as? Networkable

                    parent?.getSubcomponents()?.firstOrNull { it.networkID == networkable.networkID }
                        ?: throw IllegalStateException(
                            "${networkable::class.simpleName} with preset " +
                                "network id ${networkable.networkID} is missing in scene"
                        )
                } else {
                    event.constructorParameters?.let { networkable.constructWithParameters(it, hub!!) }
                        ?: networkable
                }

                val newWrapped = NetworkWrapper(
                    networkWrapper.networkID,
                    newAttribute,
                    networkWrapper.parents,
                    networkWrapper.publishedAt
                )

                networkObjects[networkWrapper.networkID] = newWrapped
                // update relations (and other values if created client site with constructor params)
                processUpdateEvent(event, scene)
                networkable = newAttribute

                networkWrapper.parents
                    .mapNotNull { parentId ->
                        val parent = networkObjects[parentId]?.obj as? Node
                        if (parent == null) {
                            waitingOnNetworkable[parentId] =
                                waitingOnNetworkable.getOrDefault(
                                    parentId,
                                    listOf()
                                ) + (event.copy(wrapper = newWrapped) to WaitReason.Parent)
                            null
                        } else {
                            parent
                        }
                    }
                    .forEach { parent ->
                        parent.addAttributeFromNetwork(attributeBaseClass.java, newAttribute)
                    }
            }
        }
        processWaitingNodes(networkable, scene)
    }

    private fun processWaitingNodes(parent: Networkable, scene: Scene) {
        val missingChildren = waitingOnNetworkable.remove(parent.networkID)
        missingChildren?.forEach { childEvent ->
            val reason = childEvent.second
            val event = childEvent.first
            when {
                reason == WaitReason.UpdateRelation && event is NetworkEvent.Update ->
                    processUpdateEvent(event, scene)
                reason == WaitReason.Parent && event is NetworkEvent.NewRelation ->
                    processNewRelationEvent(event)
                reason == WaitReason.Parent && event is NetworkEvent.Update && event.wrapper.obj is Node ->
                    processUpdateEvent(event, scene)
                reason == WaitReason.Parent && event is NetworkEvent.Update -> {
                    // this should be an attribute
                    val child = (event).wrapper.obj
                    val attributeBaseClass = child.getAttributeClass()!!
                    // before adding the attribute to the waitingOnParent list this was null checked
                    (parent as? Node)?.addAttributeFromNetwork(attributeBaseClass.java, child)
                }
            }
        }
    }

    override fun onClose() {
        subscriber.linger = 0
        subscriber.close()
        backchannel.linger = 0
        backchannel.close()
    }

    private enum class WaitReason {
        // Parent is missing
        Parent,

        // A related Network Object required in the update method is missing
        UpdateRelation
    }
}
