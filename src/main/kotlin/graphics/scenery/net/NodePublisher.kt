package graphics.scenery.net

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import de.javakaffee.kryoserializers.UUIDSerializer
import graphics.scenery.*
import graphics.scenery.serialization.*
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.Statistics
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.RAIVolume
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.VolumeManager
import net.imglib2.img.basictypeaccess.array.ByteArray
import org.joml.Vector3f
import org.objenesis.strategy.StdInstantiatorStrategy
import org.slf4j.Logger
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import kotlin.concurrent.thread

/**
 * Created by ulrik on 4/4/2017.
 */
class NodePublisher(
    override var hub: Hub?,
    //ip: String = "tcp://127.0.0.1",
    ip: String = "tcp://localhost",
    portPublish: Int = 7777,
    portControl: Int = 6666,
    val context: ZContext = ZContext(4)
) : Hubable {
    private val logger by LazyLogger()

    private val addressPublish = "$ip:$portPublish"
    private val addressControl = "$ip:$portControl"
    //private val addressControl = "tcp://*:5560" //"$ip:$portControl"

    //private var publishedAt = ConcurrentHashMap<Int, Long>()
    //var nodes: ConcurrentHashMap<Int, Node> = ConcurrentHashMap()
    var nodes: ConcurrentHashMap<Int, Node> = ConcurrentHashMap() //TODO delete

    private val timeout = 500
    private val publisher: ZMQ.Socket = context.createSocket(SocketType.PUB)
    var portPublish: Int = try {
        publisher.bind(addressPublish)
        addressPublish.substringAfterLast(":").toInt()
    } catch (e: ZMQException) {
        logger.warn("Binding failed, trying random port: $e")
        publisher.bindToRandomPort(addressPublish.substringBeforeLast(":"))
    }

    private val controlSubscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
    var portControl: Int = try {
        controlSubscriber.bind(addressControl)
        addressControl.substringAfterLast(":").toInt()
    } catch (e: ZMQException) {
        logger.warn("Binding failed, trying random port: $e")
        controlSubscriber.bindToRandomPort(addressControl.substringBeforeLast(":"))
    }

    val kryo = freeze()
    private val publishedObjects = ConcurrentHashMap<Int, NetworkWrapper<*>>()
    private val eventQueue = LinkedBlockingQueue<NetworkEvent>()
    private var index = 1

    private var publishing = false
    private var listeningForControl = false

    private fun generateNetworkID() = index++

    init {
        controlSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)
    }

    fun register(scene: Scene) {
        val sceneNo = NetworkWrapper(generateNetworkID(), scene, mutableListOf())
        publishedObjects[sceneNo.networkID] = sceneNo
        addUpdateEvent(sceneNo)

        scene.onChildrenAdded["networkPublish"] = { _, child -> registerNode(child) }
        scene.onChildrenRemoved["networkPublish"] = { parent, child -> detachNode(child,parent) }
        scene.onAttributeAdded["NetworkPublish"] = {node, attribute -> addAttribute(node,attribute) }

        // abusing the discover function for a tree walk
        scene.discover(scene, { registerNode(it); false })
    }

    private fun registerNode(node: Node) {
        if (!node.wantsSync()) {
            return
        }
        if (node.parent == null) {
            throw IllegalArgumentException("Node not part of scene graph and cant be synchronized alone")
        }
        val parentId = node.parent?.networkID
        if (parentId == null || publishedObjects[parentId] == null) {
            throw IllegalArgumentException("Node Parent not registered with publisher.")
        }

        if (publishedObjects[node.networkID] == null) {
            val wrapper = NetworkWrapper(generateNetworkID(), node, mutableListOf(parentId))
            addUpdateEvent(wrapper)
            publishedObjects[wrapper.networkID] = wrapper
        } else {
            // parent change
            val wrapper = publishedObjects[node.networkID]
            wrapper?.parents?.add(parentId)
            eventQueue.add(NetworkEvent.NewRelation(parentId, node.networkID))
        }

        node.getSubcomponents().forEach { subComponent ->
            val subNetObj = publishedObjects[subComponent.networkID]
            if (subNetObj != null) {
                subNetObj.parents.add(node.networkID)
                eventQueue.add(NetworkEvent.NewRelation(node.networkID, subComponent.networkID))
            } else {
                val new = NetworkWrapper(generateNetworkID(), subComponent, mutableListOf(node.networkID))
                publishedObjects[new.networkID] = new
                addUpdateEvent(new)
            }
        }
    }

    private fun detachNode(node: Node, parent: Node) {
        publishedObjects[node.networkID]?.let {
            it.parents.remove(parent.networkID)
            eventQueue.add(NetworkEvent.NewRelation(null, node.networkID))
        }
    }

    private fun addAttribute(node: Node, attribute: Any) {
        if (attribute !is Networkable){
            return
        }
        if (!publishedObjects.containsKey(node.networkID)){
            // this relation and attribute will be published with the registration of the parent node
            return
        }
        val attributeWrapper = publishedObjects[attribute.networkID]

        if (attributeWrapper == null){
            val new = NetworkWrapper(generateNetworkID(), attribute, mutableListOf(node.networkID))
            publishedObjects[new.networkID] = new
            addUpdateEvent(new)
        } else {
            attributeWrapper.parents.add(node.networkID)
            eventQueue.add(NetworkEvent.NewRelation(node.networkID, attribute.networkID))
        }
    }

    /**
     * Should be called in the update phase of the life cycle
     */
    fun scanForChanges() {
        for (it in publishedObjects.values) {
            if (it.obj.lastChange() >= it.publishedAt) {
                it.publishedAt = System.nanoTime()
                addUpdateEvent(it)
            }
        }
    }

    fun startPublishing() {
        publishing = true
        thread {
            while (publishing) {
                val event = eventQueue.poll(timeout.toLong(), TimeUnit.MILLISECONDS) ?: continue
                if (!publishing) break // in case of shutdown while polling
                if (event is NetworkEvent.RequestInitialization) {
                    publishedObjects.forEach {
                        addUpdateEvent(it.value)
                    }
                }
                val start = System.nanoTime()
                val payloadSize = sendEvent(event, kryo, publisher, logger)
                val duration = (System.nanoTime() - start).toFloat()
                (hub?.get(SceneryElement.Statistics) as? Statistics)?.add("Serialise.duration", duration)
                (hub?.get(SceneryElement.Statistics) as? Statistics)?.add(
                    "Serialise.payloadSize",
                    payloadSize,
                    isTime = false
                )
            }
        }
        startListeningControl()
    }

    private fun addUpdateEvent(wrapper: NetworkWrapper<*>) {
        wrapper.additionalData = wrapper.obj.getAdditionalData()
        eventQueue.add(NetworkEvent.Update(wrapper))
    }

    fun startListeningControl() {
        listeningForControl = true
        controlSubscriber.receiveTimeOut = timeout
        thread {
            while (listeningForControl) {
                try {
                    val payload: kotlin.ByteArray = controlSubscriber.recv() ?: continue

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

    fun debugPublish(send: (NetworkEvent) -> Unit) {
        while (eventQueue.isNotEmpty()) {
            send(eventQueue.poll())
        }
    }

    fun close(waitForWorkerAndListeners: Boolean = false) {
        publishing = false
        listeningForControl = false
        if (waitForWorkerAndListeners) {
            Thread.sleep(timeout * 2L)
        }
        context.destroySocket(publisher)
        context.close()
    }

    companion object {
        fun sendEvent(event: NetworkEvent, kryo: Kryo, socket: ZMQ.Socket, logger: Logger): Long {
            var payloadSize = 0L
            try {
                val bos = ByteArrayOutputStream()
                val output = Output(bos)
                kryo.writeClassAndObject(output, event)
                output.flush()

                val payload = bos.toByteArray()
                socket.send(payload)
                Thread.sleep(1)
                payloadSize = payload.size.toLong()

                output.close()
                bos.close()
            } catch (e: IOException) {
                logger.warn("Error in publishing: ${event.javaClass.name}", e)
            } catch (e: AssertionError) {
                logger.warn("Error in publishing: ${event.javaClass.name}", e)
            } catch (e: Throwable) {
                print(e)
            }
            return payloadSize
        }

        fun freeze(): Kryo {
            val kryo = Kryo()
            kryo.instantiatorStrategy = DefaultInstantiatorStrategy(StdInstantiatorStrategy())
            kryo.isRegistrationRequired = false
            kryo.references = true
            kryo.setCopyReferences(true)
            kryo.register(UUID::class.java, UUIDSerializer())
            kryo.register(OrientedBoundingBox::class.java, OrientedBoundingBoxSerializer())
            kryo.register(Triple::class.java, TripleSerializer())
            kryo.register(ByteBuffer::class.java, ByteBufferSerializer())

            // A little trick here, because DirectByteBuffer is package-private
            val tmp = ByteBuffer.allocateDirect(1)
            kryo.register(tmp.javaClass, ByteBufferSerializer())
            kryo.register(ByteArray::class.java, Imglib2ByteArraySerializer())
            kryo.register(ShaderMaterial::class.java, ShaderMaterialSerializer())
            kryo.register(java.util.zip.Inflater::class.java, IgnoreSerializer<Inflater>())
            kryo.register(VolumeManager::class.java, IgnoreSerializer<VolumeManager>())
            kryo.register(Vector3f::class.java, Vector3fSerializer())

            kryo.register(Volume::class.java, VolumeSerializer())
            kryo.register(RAIVolume::class.java, VolumeSerializer())
            kryo.register(BufferedVolume::class.java, VolumeSerializer())

            return kryo
        }
    }
}
