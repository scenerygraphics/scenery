package graphics.scenery.net

import com.esotericsoftware.kryo.io.Input
import graphics.scenery.*
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.Statistics
import graphics.scenery.volumes.Volume
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.ByteArrayInputStream
import java.io.StreamCorruptedException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


/**
 * Created by ulrik on 4/4/2017.
 */
class NodeSubscriber(
    override var hub: Hub?,
    val address: String = "tcp://localhost:6666",
    val context: ZContext = ZContext(4)
) : Hubable {

    private val logger by LazyLogger()
    var nodes: ConcurrentHashMap<Int, Node> = ConcurrentHashMap()
    var subscriber: ZMQ.Socket = context.createSocket(ZMQ.SUB)
    val kryo = NodePublisher.freeze()
    private val networkObjects = hashMapOf<Int, NetworkObject<*>>()
    private val eventQueue = LinkedBlockingQueue<NetworkEvent>()
    private val waitingOnParent = mutableMapOf<Int, List<NetworkEvent>>()
    private var running = false

    init {
        subscriber.connect(address)
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)
    }

    fun startListening() {
        running = true
        subscriber.receiveTimeOut = 0
        thread {
            while (running) {
                try {
                    var payload: ByteArray? = subscriber.recv()
                    while (payload != null && running) {
                        try {
                            val bin = ByteArrayInputStream(payload)
                            val input = Input(bin)
                            val event = kryo.readClassAndObject(input) as? NetworkEvent
                                ?: throw IllegalStateException("Received unknown, not NetworkEvent payload")
                            eventQueue.add(event)
                            payload = subscriber.recv()

                        } catch (t: Throwable) {
                            print(t)
                        }
                    }
                } catch (t: Throwable) {
                    print(t)
                }
                Thread.sleep(50)
            }
        }
    }

    /**
     * Used in Unit test
     */
    private fun debugListen(event: NetworkEvent) {
        eventQueue.add(event)
    }

    /**
     * Should be called in update life cycle
     */
    fun networkUpdate(scene: Scene) {
        while (!eventQueue.isEmpty()) {
            when (val event = eventQueue.poll()) {
                is NetworkEvent.NewObject -> {
                    val networkObject = event.obj

                    fun reuniteChildParent(parent: Node) {
                        val missingChildren = waitingOnParent.remove(parent.networkID)
                        missingChildren?.forEach { childEvent ->
                            when (childEvent) {
                                is NetworkEvent.NewObject -> {
                                    when (val child = childEvent.obj.obj) {
                                        is Node -> {
                                            parent.addChild(child)
                                        }
                                        else -> {
                                            // assuming child is Attribute
                                            val attributeBaseClass = child.getAttributeClass()!!
                                            // before adding the attribute to the waitingOnParent list this was null checked
                                            parent.addAttributeFromNetwork(attributeBaseClass.java, parent)
                                        }
                                    }
                                }
                                is NetworkEvent.NewRelation -> TODO()
                            }
                        }
                    }

                    when (val networkable = networkObject.obj) {
                        is Scene -> {
                            // dont use the scene from network, but adapt own scene
                            scene.networkID = networkable.networkID
                            scene.update(networkable)
                            networkObjects[networkObject.networkID] = NetworkObject(networkObject.networkID, scene, mutableListOf())
                            reuniteChildParent(scene)
                        }
                        is Node -> {
                            if (networkObjects.containsKey(networkable.networkID)){
                                networkObjects[networkable.networkID]?.obj?.update(networkable)
                                continue
                            }
                            val parentId = networkObject.parents.first()
                            val parent = networkObjects[parentId]?.obj as? Node
                            if (parent != null) {
                                parent.addChild(networkable)
                            } else {
                                waitingOnParent[parentId] = waitingOnParent.getOrDefault(parentId, listOf()) + event
                            }
                            networkObjects[networkObject.networkID] = networkObject
                            reuniteChildParent(networkable)
                        }
                        else -> {
                            val attributeBaseClass = networkable.getAttributeClass()
                            if (attributeBaseClass != null) {
                                // val r = cast(attributeBaseClass,networkable)
                                // It is an attribute
                                networkObject.parents
                                    .mapNotNull { parentId ->
                                        val parent = networkObjects[parentId]?.obj as? Node
                                        if (parent == null) {
                                            waitingOnParent[parentId] =
                                                waitingOnParent.getOrDefault(parentId, listOf()) + event
                                            null
                                        } else {
                                            parent
                                        }
                                    }
                                    .forEach {
                                        it.addAttributeFromNetwork(attributeBaseClass.java, networkable)
                                        it.spatialOrNull()?.needsUpdate = true
                                    }
                                networkObjects[networkObject.networkID] = networkObject
                            } else {
                                throw IllegalStateException(
                                    "Received unknown object from server. " +
                                        "Maybe an attribute missing a getAttributeClass implementation?"
                                )
                            }
                        }
                    }
                }
                is NetworkEvent.NewRelation -> TODO()
                is NetworkEvent.Update -> {
                    val fresh = event.obj.obj
                    val tmp =  networkObjects[fresh.networkID]?.obj
                        ?: throw Exception("Got update for unknown object with id ${fresh.networkID} and class ${fresh.javaClass.simpleName}")
                    tmp.update(fresh)
                }
            }
        }
    }

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

                        if (Volume::class.java.isAssignableFrom(o.javaClass) && Volume::class.java.isAssignableFrom(node.javaClass)) {
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

    fun close() {
        context.destroySocket(subscriber)
        context.close()
    }
}
