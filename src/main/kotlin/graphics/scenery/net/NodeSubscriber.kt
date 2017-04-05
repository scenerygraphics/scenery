package graphics.scenery.net

import graphics.scenery.Node
import org.slf4j.LoggerFactory
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.*

/**
 * Created by ulrik on 4/4/2017.
 */
class NodeSubscriber(val address: String = "tcp://localhost:6666", val context: ZContext) {

    var logger = LoggerFactory.getLogger("NodeSubscriber")
    var nodes: HashMap<String, Node> = HashMap()
    var subscriber: ZMQ.Socket = context.createSocket(ZMQ.SUB)

    init {
        subscriber.connect(address)
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)
    }

    fun process() {
        while(true) {
            try {
                val id = subscriber.recvStr()
                val size = subscriber.recvStr()

                logger.trace("Received for $id of length $size")
                val payload = ByteArray(size.toInt())
                val msg = subscriber.recv(payload, 0, size.toInt(), 0)

                var node = nodes.get(id)
                if (node != null) {
                    val bin = ByteArrayInputStream(payload)
                    val node_in = ObjectInputStream(bin)
                    val o: Node = node_in.readObject() as Node

                    logger.trace("Replacing node ${node.name} with ${o.name}")
                    node.position = o.position
                    bin.close()
                }
            } catch(e: StreamCorruptedException) {
                logger.warn("Corrupted stream")
            }
        }
    }

    fun close() {
        context.destroySocket(subscriber)
    }
}
