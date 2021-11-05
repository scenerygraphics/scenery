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
import kotlin.reflect.KClass


/**
 * Created by ulrik on 4/4/2017.
 */
class NodeSubscriber(override var hub: Hub?, val address: String = "tcp://localhost:6666", val context: ZContext = ZContext(4)) : Hubable {

    private val logger by LazyLogger()
    var nodes: ConcurrentHashMap<Int, Node> = ConcurrentHashMap()
    var subscriber: ZMQ.Socket = context.createSocket(ZMQ.SUB)
    val kryo = NodePublisher.freeze()
    private val networkObjects = hashMapOf<Int, NetworkObject<*>>()
    private val eventQueue = LinkedBlockingQueue<NetworkEvent>()
    private var running = false

    init {
        subscriber.connect(address)
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)
    }

    fun startListening() {
        running = true
        thread {
            while (running) {
                var payload: ByteArray? = subscriber.recv()
                while (payload != null && running) {
                    try {
                        val bin = ByteArrayInputStream(payload)
                        val input = Input(bin)
                        val event = kryo.readClassAndObject(input) as? NetworkEvent
                            ?: throw IllegalStateException("Received unknown, not NetworkEvent payload")
                        eventQueue.add(event)
                        payload = subscriber.recv()

                    } catch (ex: Exception) {
                        println()
                    }
                }
                Thread.sleep(5)
            }
        }
    }

    fun debugListen(event: NetworkEvent){
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
                    when (val networkable = networkObject.obj) {
                        is Scene -> {
                            scene.networkID = networkable.networkID
                            scene.update(networkable)
                            networkObjects[networkObject.nID] = NetworkObject(networkObject.nID, scene, mutableListOf())
                        }
                        is Node -> {
                            val parent = networkObjects[networkObject.parents.first()]?.obj as? Node
                            if (parent != null){
                                parent.addChild(networkable)
                            } else {
                                throw IllegalStateException("Cant find parent of Node ${networkable.name} for network sync.")
                            }
                            networkObjects[networkObject.nID] = networkObject
                        }
                        else -> {
                            val attributeBaseClass = networkable.getAttributeClass()
                            if (attributeBaseClass != null) {
                                //val r = cast(attributeBaseClass,networkable)
                                // It is an attribute
                                networkObject.parents
                                    .map {
                                        networkObjects[it] as? Node
                                            ?: throw IllegalStateException("Cant find parent attribute for network sync.")
                                    }
                                    .forEach {
                                        it.addAttributeFromNetwork(attributeBaseClass.java, networkable)
                                    }
                                networkObjects[networkObject.nID] = networkObject
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
            }
        }
    }

    inline fun <reified T : Any> cast(attributeType: KClass<T>, attribute: Networkable): T? {
        return attribute as? T
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
                            if(node.currentTimepoint != o.currentTimepoint) {
                                node.goToTimepoint(o.currentTimepoint)
                            }
                        }

                        if(o is PointLight && node is PointLight) {
                            node.emissionColor = o.emissionColor
                            node.lightRadius = o.lightRadius
                        }

                        if(o is BoundingGrid && node is BoundingGrid) {
                            node.gridColor = o.gridColor
                            node.lineWidth = o.lineWidth
                            node.numLines = o.numLines
                            node.ticksOnly = o.ticksOnly
                        }

                        input.close()
                        bin.close()
                    }
                }
            } catch(e: StreamCorruptedException) {
                logger.warn("Corrupted stream")
            } catch(e: NullPointerException) {
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
