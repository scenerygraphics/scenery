package graphics.scenery.net

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import de.javakaffee.kryoserializers.UUIDSerializer
import graphics.scenery.*
import graphics.scenery.serialization.*
import graphics.scenery.utils.lazyLogger
import graphics.scenery.utils.Statistics
import graphics.scenery.volumes.VolumeManager
import net.imglib2.img.basictypeaccess.array.ByteArray
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.BondImpl
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
 * Server of scenery networking.
 *
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 */
class NodePublisher(
    override var hub: Hub?,
    //ip: String = "tcp://127.0.0.1",
    ip: String = "tcp://localhost",
    portMain: Int = 7777,
    portBackchannel: Int = 6666,
    val context: ZContext
) : Hubable {
    private val logger by lazyLogger()

    private val addressMain = "$ip:$portMain"
    private val addressBackchannel = "$ip:$portBackchannel"

    var timeout = 15 // -> 60fps
    private val publisher: ZMQ.Socket = context.createSocket(SocketType.PUB)
    var portMain: Int = try {
        publisher.bind(addressMain)
        addressMain.substringAfterLast(":").toInt()
    } catch (e: ZMQException) {
        logger.warn("Binding failed, trying random port: $e")
        publisher.bindToRandomPort(addressMain.substringBeforeLast(":"))
    }

    private val backchannelSubscriber: ZMQ.Socket = context.createSocket(SocketType.SUB)
    var portBackchannel: Int = try {
        backchannelSubscriber.bind(addressBackchannel)
        addressBackchannel.substringAfterLast(":").toInt()
    } catch (e: ZMQException) {
        logger.warn("Binding failed, trying random port: $e")
        backchannelSubscriber.bindToRandomPort(addressBackchannel.substringBeforeLast(":"))
    }

    val kryo = freeze()
    private val publishedObjects = ConcurrentHashMap<Int, NetworkWrapper<*>>()
    private val eventQueue = LinkedBlockingQueue<NetworkEvent>()
    private var index = 1

    private val publishWorker = PublishWorker(this)
    private val backchannelWorker = BackchannelWorker(this)

    private fun generateNetworkID() = index++

    init {
        logger.info("Server opened main channel at $ip:${this.portMain} and back channel at $ip:${this.portBackchannel}")
        backchannelSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)
        backchannelSubscriber.receiveTimeOut = timeout
        publishWorker.startAgent()
        backchannelWorker.startAgent()
    }

    /**
     * Needs to be called at initialization.
     */
    internal fun register(scene: Scene) {
        val sceneNo = NetworkWrapper(generateNetworkID(), scene, mutableListOf())
        publishedObjects[sceneNo.networkID] = sceneNo
        addUpdateEvent(sceneNo)

        scene.onChildrenAdded["networkPublish"] = { _, child -> registerNode(child) }
        scene.onChildrenRemoved["networkPublish"] = { parent, child -> detachNode(child, parent) }
        scene.onAttributeAdded["networkPublish"] = { node, attribute -> registerAttribute(node, attribute) }

        scene.children.forEach(::registerNode)
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
            val wrapper = NetworkWrapper(
                // negative network id is reserved for preregistered objects
                if (node.networkID < -1) node.networkID else generateNetworkID(),
                node, mutableListOf(parentId))
            addUpdateEvent(wrapper)
            publishedObjects[wrapper.networkID] = wrapper
        } else {
            // parent change
            val wrapper = publishedObjects[node.networkID]
            wrapper?.parents?.add(parentId)
            eventQueue.add(NetworkEvent.NewRelation(parentId, node.networkID))
        }

        node.getSubcomponents().forEach { subComponent ->
            if (!subComponent.wantsSync()){
                return@forEach
            }
            val subNetObj = publishedObjects[subComponent.networkID]
            if (subNetObj != null) {
                subNetObj.parents.add(node.networkID)
                eventQueue.add(NetworkEvent.NewRelation(node.networkID, subComponent.networkID))
            } else {
                val new = NetworkWrapper(
                    // negative network id is reserved for preregistered objects
                    if (subComponent.networkID < -1) subComponent.networkID else generateNetworkID(),
                    subComponent, mutableListOf(node.networkID))
                publishedObjects[new.networkID] = new
                addUpdateEvent(new)
            }
        }

        node.children.forEach(::registerNode)
    }

    private fun detachNode(node: Node, parent: Node) {
        publishedObjects[node.networkID]?.let {
            it.parents.remove(parent.networkID)
            eventQueue.add(NetworkEvent.NewRelation(null, node.networkID))
        }
    }

    private fun registerAttribute(node: Node, attribute: Any) {
        if (attribute !is Networkable) {
            return
        }
        if (!publishedObjects.containsKey(node.networkID)) {
            // this relation and attribute will be published with the registration of the parent node
            return
        }
        val attributeWrapper = publishedObjects[attribute.networkID]

        if (attributeWrapper == null) {
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
            if (it.obj.modifiedAt >= it.publishedAt) {
                it.publishedAt = System.nanoTime()
                addUpdateEvent(it)
            }
        }
    }


    private fun addUpdateEvent(wrapper: NetworkWrapper<*>) {
        eventQueue.add(
            NetworkEvent.Update(
                wrapper
            )
        )
    }

    fun debugPublish(send: (NetworkEvent) -> Unit) {
        while (eventQueue.isNotEmpty()) {
            send(eventQueue.poll())
        }
    }

    fun close(): Thread = thread {
        val pw = publishWorker.close()
        val bw = backchannelWorker.close()
        pw.join()
        bw.join()
    }

    private class PublishWorker(val parent: NodePublisher): Agent(){
        override fun onLoop() {
            try {
                val event = parent.eventQueue.poll(parent.timeout.toLong(),TimeUnit.MILLISECONDS)
                event?.let { processNextNetworkEvent(it) }
            } catch (t: ZMQException){
                if (t.errorCode != 4) {//Errno 4 : Interrupted function
                    // Interrupted exceptions are expected when closing the publisher and no need to worry
                    throw t
                }
            }
        }

        private fun processNextNetworkEvent(event: NetworkEvent) {
            if (event is NetworkEvent.RequestInitialization) {
                parent.publishedObjects.forEach {
                    parent.addUpdateEvent(it.value)
                }
            }
            val start = System.nanoTime()
            val payloadSize = sendEvent(event, parent.kryo, parent.publisher, parent.logger)
            val duration = (System.nanoTime() - start).toFloat()
            (parent.hub?.get(SceneryElement.Statistics) as? Statistics)?.add("Serialise.duration", duration)
            (parent.hub?.get(SceneryElement.Statistics) as? Statistics)?.add(
                "Serialise.payloadSize",
                payloadSize,
                isTime = false
            )
        }

        override fun onClose() {
            parent.publisher.linger = 0
            parent.publisher.close()
        }

    }

    private class BackchannelWorker(val parent: NodePublisher): Agent(){
        override fun onLoop() {
            try {
                val payload = parent.backchannelSubscriber.recv()
                payload?.let { listenToControlChannel(it) }
                if (payload == null) {
                    Thread.sleep(parent.timeout.toLong())
                }
            } catch (t: ZMQException){
                if (t.errorCode != 4) {//Errno 4 : Interrupted function
                    // Interrupted exceptions are expected when closing the publisher and no need to worry
                    throw t
                }
            }
        }

        private fun listenToControlChannel(payload: kotlin.ByteArray) {
            try {
                val bin = ByteArrayInputStream(payload)
                val input = Input(bin)
                val event = parent.kryo.readClassAndObject(input) as? NetworkEvent
                    ?: throw IllegalStateException("Received unknown, not NetworkEvent payload")
                parent.eventQueue.add(event)

            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        override fun onClose() {
            parent.backchannelSubscriber.linger = 0
            parent.backchannelSubscriber.close()
        }
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
                e.printStackTrace()
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
            kryo.register(Inflater::class.java, IgnoreSerializer<Inflater>())
            kryo.register(org.biojava.nbio.structure.BondImpl::class.java, IgnoreSerializer<BondImpl>())
            kryo.register(org.biojava.nbio.structure.AtomImpl::class.java, IgnoreSerializer<AtomImpl>())
            kryo.register(VolumeManager::class.java, IgnoreSerializer<VolumeManager>())
            kryo.register(Vector3f::class.java, Vector3fSerializer())

            return kryo
        }
    }
}
