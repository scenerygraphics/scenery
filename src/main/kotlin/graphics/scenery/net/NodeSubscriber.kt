package graphics.scenery.net

import com.esotericsoftware.kryo.io.Input
import graphics.scenery.*
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
    portControl: Int = 6666,
    val context: ZContext = ZContext(4),
    val init: Boolean = true
) : Hubable {
    private val addressSubscribe = "$ip:$portPublish"
    //private val addressControl = "tcp://localhost:5560"
    private val addressControl = "$ip:$portControl"

    private val logger by LazyLogger()
    var nodes: ConcurrentHashMap<Int, Node> = ConcurrentHashMap()
    var subscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
    var control: ZMQ.Socket = context.createSocket(SocketType.PUB)
    val kryo = NodePublisher.freeze()
    private val networkObjects = hashMapOf<Int, NetworkWrapper<*>>()
    private val eventQueue = LinkedBlockingQueue<NetworkEvent>()
    private val waitingOnNetworkable = mutableMapOf<Int, List<Pair<NetworkEvent, WaitReason>>>()
    private var running = false

    init {
        if (init){
            subscriber.connect(addressSubscribe)
            subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)
            control.connect(addressControl)
            GlobalScope.launch {
                delay(1000)
                NodePublisher.sendEvent(NetworkEvent.RequestInitialization(),kryo,control,logger)
            }
        }
    }

    fun startListening() {
        running = true
        subscriber.receiveTimeOut = 0
        thread {
            while (running) {
                try {
                    var payload: ByteArray? = subscriber.recv()
                    while (payload != null && running) {
                        val bin = ByteArrayInputStream(payload)
                        val input = Input(bin)
                        val event = kryo.readClassAndObject(input) as? NetworkEvent
                            ?: throw IllegalStateException("Received unknown, not NetworkEvent payload")
                        eventQueue.add(event)
                        payload = subscriber.recv()

                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
                Thread.sleep(50)
            }
        }
    }

    fun stopListening(){
        running = false
    }

    /**
     * Used in Unit test
     */
    private fun debugListen(event: NetworkEvent) {
        eventQueue.add(event)
    }

    class NetworkableNotFoundException(val id: Int): IllegalStateException()
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
                    processNewRelationEvent(event,scene)
                }
            }
        }
    }

    private fun processNewRelationEvent(event: NetworkEvent.NewRelation, scene: Scene) {
        val parent = event.parent?.let { networkObjects[it]  }?.obj as? Node
        if (event.parent != null && parent == null){
            waitingOnNetworkable.getOrDefault(event.parent, listOf()) + (event to WaitReason.UpdateRelation)
            return
        }
        val childWrapper = networkObjects[event.child]
        if (childWrapper == null){
            waitingOnNetworkable.getOrDefault(event.child, listOf()) + (event to WaitReason.UpdateRelation)
            return
        }
        when (val child = childWrapper.obj){
            is Node -> {
                if (parent == null){
                    child.parent?.removeChild(child)
                } else {
                    parent.addChild(child)
                }
            }
            else -> {
                // Attribute
                parent?.addAttributeFromNetwork(child.getAttributeClass()!!.java,child)
            }
        }
    }

    private fun processUpdateEvent(event: NetworkEvent.Update, scene: Scene){
        val networkWrapper = event.obj

        // ---------- update -------------
        if (networkObjects.containsKey(networkWrapper.networkID)) {
            val fresh = networkWrapper.obj
            val tmp = networkObjects[fresh.networkID]?.obj
                ?: throw Exception("Got update for unknown object with id ${fresh.networkID} and class ${fresh.javaClass.simpleName}")
            try {
                tmp.update(fresh,this::getNetworkable, networkWrapper.additionalData)
            } catch (e: NetworkableNotFoundException){
                waitingOnNetworkable[e.id] = waitingOnNetworkable.getOrDefault(e.id, listOf()) + (event to WaitReason.UpdateRelation)
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
                    scene.update(networkable, this::getNetworkable,networkWrapper.additionalData)
                } catch (e: NetworkableNotFoundException){
                    waitingOnNetworkable[e.id] = waitingOnNetworkable.getOrDefault(e.id, listOf())+ (event to WaitReason.Parent)
                }
                networkObjects[networkWrapper.networkID] = NetworkWrapper(networkWrapper.networkID, scene, mutableListOf())
                networkable = scene
            }
            is Node -> {
                networkable.initialized = false
                val parentId = networkWrapper.parents.first()
                val parent = networkObjects[parentId]?.obj as? Node
                if (parent != null) {
                    parent.addChild(networkable)
                } else {
                    waitingOnNetworkable[parentId] = waitingOnNetworkable.getOrDefault(parentId, listOf())+ (event to WaitReason.Parent)
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
        processWaitingNodes(networkable,scene)
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
                        is NetworkEvent.NewRelation -> processNewRelationEvent(event, scene)
                    }
                }
            }
        }
    }
/*
    fun process() {
        while (true) {
            var start: Long
            var duration: Long = 0L
            try {
                start = System.nanoTime()
                val id = subscriber.recvStr().toInt()

                logger.trace("Received {} for deserializiation", id)
                duration = (System.nanoTime() - start)
                val payload = subscriber.recv()

                if (payload != null) {
                    logger.trace("payload is not null, node id={}, have={}", id, nodes.containsKey(id))
                    nodes[id]?.let { node ->
                        val bin = ByteArrayInputStream(payload)
                        val input = Input(bin)
                        val o = kryo.readClassAndObject(input) as? Node ?: return@let

                        val oSpatial = o.spatialOrNull()
                        if (oSpatial != null) {
                            node.ifSpatial {
                                position = oSpatial.position
                                rotation = oSpatial.rotation
                                scale = oSpatial.scale
                            }
                        }
                        node.visible = o.visible

                        node.ifMaterial {
                            o.materialOrNull()?.let {
                                diffuse = it.diffuse
                                blending = it.blending
                            }
                        }

                        if (Volume::class.java.isAssignableFrom(o.javaClass)
                            && Volume::class.java.isAssignableFrom(node.javaClass)) {
                            (node as Volume).colormap = (o as Volume).colormap
                            node.transferFunction = o.transferFunction
                            if (node.currentTimepoint != o.currentTimepoint) {
                                node.goToTimepoint(o.currentTimepoint)
                            }
                        }

                        if (o is PointLight && node is PointLight) {
                            node.emissionColor = o.emissionColor
                            node.lightRadius = o.lightRadius
                        }

                        if (o is BoundingGrid && node is BoundingGrid) {
                            node.gridColor = o.gridColor
                            node.lineWidth = o.lineWidth
                            node.numLines = o.numLines
                            node.ticksOnly = o.ticksOnly
                        }

                        input.close()
                        bin.close()
                    }
                }
            } catch (e: StreamCorruptedException) {
                logger.warn("Corrupted stream")
            } catch (e: NullPointerException) {
                logger.warn("NPE while receiving payload: $e")
            }

            hub?.get<Statistics>(SceneryElement.Statistics)?.add("Deserialise", duration.toFloat())

        }
    }

 */

    fun close() {
        context.destroySocket(subscriber)
        context.close()
    }

    private enum class WaitReason(){
        Parent, UpdateRelation
    }
}
