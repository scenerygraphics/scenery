package graphics.scenery.net

import com.esotericsoftware.kryo.io.Input
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.utils.LazyLogger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


/**
 * Created by ulrik on 4/4/2017.
 */
class NodeSubscriber(
    override var hub: Hub?,
    ip: String = "tcp://localhost",
    portPublish: Int = 7777,
    portBackchannel: Int = 6666,
    val context: ZContext = ZContext(4),
    startNetworkActivity: Boolean = true
) : Hubable {
    private val logger by LazyLogger()
    val kryo = NodePublisher.freeze()

    private val addressSubscribe = "$ip:$portPublish"
    private val addressBackchannel = "$ip:$portBackchannel"
    var subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
    var backchannel: ZMQ.Socket = context.createSocket(SocketType.PUB)

    private val networkObjects = hashMapOf<Int, NetworkWrapper<*>>()
    private val eventQueue = LinkedBlockingQueue<NetworkEvent>()
    private val waitingOnNetworkable = mutableMapOf<Int, List<Pair<NetworkEvent, WaitReason>>>()
    private var listening = false

    init {
        if (startNetworkActivity) {
            if (subscriber.connect(addressSubscribe)){
                logger.info("Client connected to main channel at $addressSubscribe")
            }
            subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)
            if(backchannel.connect(addressBackchannel)){
                logger.info("Client connected to back channel at $addressBackchannel")
            }
            GlobalScope.launch {
                delay(1000)
                NodePublisher.sendEvent(NetworkEvent.RequestInitialization(), kryo, backchannel, logger)
            }
        }
    }

    fun startListening() {
        listening = true
        subscriber.receiveTimeOut = 100
        thread {
            while (listening) {
                try {
                    val payload: ByteArray = subscriber.recv() ?: continue

                    val bin = ByteArrayInputStream(payload)
                    val input = Input(bin)
                    val event = kryo.readClassAndObject(input) as? NetworkEvent
                        ?: throw IllegalStateException("Received unknown, not NetworkEvent payload")
                    eventQueue.add(event)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    }

    fun stopListening() {
        listening = false
    }

    /**
     * Used in Unit test
     */
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
        val networkWrapper = event.obj

        // ---------- update -------------
        if (networkObjects.containsKey(networkWrapper.networkID)) {
            val fresh = networkWrapper.obj
            val tmp = networkObjects[fresh.networkID]?.obj
                ?: throw Exception("Got update for unknown object with id ${fresh.networkID} and class ${fresh.javaClass.simpleName}")
            try {
                tmp.update(fresh, this::getNetworkable, networkWrapper.additionalData)
            } catch (e: NetworkableNotFoundException) {
                waitingOnNetworkable[e.id] =
                    waitingOnNetworkable.getOrDefault(e.id, listOf()) + (event to WaitReason.UpdateRelation)
            }
            return
        }

        // ------------ new object -----------
        var networkable = networkWrapper.obj
        when (networkable) {
            is Scene -> {
                // dont use the scene from network, but adapt own scene
                scene.networkID = networkable.networkID
                try {
                    scene.update(networkable, this::getNetworkable, networkWrapper.additionalData)
                } catch (e: NetworkableNotFoundException) {
                    waitingOnNetworkable[e.id] =
                        waitingOnNetworkable.getOrDefault(e.id, listOf()) + (event to WaitReason.Parent)
                }
                networkObjects[networkWrapper.networkID] =
                    NetworkWrapper(networkWrapper.networkID, scene, mutableListOf())
                networkable = scene
            }
            is Node -> {
                networkable.initialized = false
                val parentId = networkWrapper.parents.first()
                val parent = networkObjects[parentId]?.obj as? Node
                if (parent != null) {
                    parent.addChild(networkable)
                } else {
                    waitingOnNetworkable[parentId] =
                        waitingOnNetworkable.getOrDefault(parentId, listOf()) + (event to WaitReason.Parent)
                }
                networkObjects[networkWrapper.networkID] = networkWrapper
            }
            else -> {
                val attributeBaseClass = networkable.getAttributeClass()
                if (attributeBaseClass != null) {
                    // It is an attribute
                    networkWrapper.parents
                        .mapNotNull { parentId ->
                            val parent = networkObjects[parentId]?.obj as? Node
                            if (parent == null) {
                                waitingOnNetworkable[parentId] =
                                    waitingOnNetworkable.getOrDefault(parentId, listOf()) + (event to WaitReason.Parent)
                                null
                            } else {
                                parent
                            }
                        }
                        .forEach {
                            it.addAttributeFromNetwork(attributeBaseClass.java, networkable)
                            it.spatialOrNull()?.needsUpdate = true
                        }
                    networkObjects[networkWrapper.networkID] = networkWrapper
                } else {
                    throw IllegalStateException(
                        "Received unknown object from server. ${networkable.javaClass.simpleName}" +
                            "Maybe an attribute missing a getAttributeClass implementation?"
                    )
                }
            }
        }
        eventQueue.add(NetworkEvent.Update(networkWrapper)) // update relations if this is a post-initial-sync new object
        processWaitingNodes(networkable, scene)
    }

    private fun processWaitingNodes(parent: Networkable, scene: Scene) {
        val missingChildren = waitingOnNetworkable.remove(parent.networkID)
        missingChildren?.forEach { childEvent ->
            val reason = childEvent.second
            val event = childEvent.first
            when (reason) {
                WaitReason.UpdateRelation -> {
                    if (event is NetworkEvent.Update) {
                        processUpdateEvent(event, scene)
                    }
                }
                WaitReason.Parent -> {
                    when (event) {
                        is NetworkEvent.Update -> {
                            when (val child = (event).obj.obj) {
                                is Node -> {
                                    (parent as? Node)?.addChild(child)
                                }
                                else -> {
                                    // assuming child is Attribute
                                    val attributeBaseClass = child.getAttributeClass()!!
                                    // before adding the attribute to the waitingOnParent list this was null checked
                                    (parent as? Node)?.addAttributeFromNetwork(attributeBaseClass.java, child)
                                }
                            }
                        }
                        is NetworkEvent.NewRelation -> processNewRelationEvent(event)
                    }
                }
            }
        }
    }

    fun close() {
        stopListening()
        context.destroySocket(subscriber)
        context.destroySocket(backchannel)
        context.close()
    }

    private enum class WaitReason {
        Parent, UpdateRelation
    }
}
