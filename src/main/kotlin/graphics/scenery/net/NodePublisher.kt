package graphics.scenery.net

import graphics.scenery.Node
import org.slf4j.LoggerFactory
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectOutputStream

/**
 * Created by ulrik on 4/4/2017.
 */

class NodePublisher(val address: String = "tcp://*:6666", val context: ZContext) {
    var logger = LoggerFactory.getLogger("NodePublisher")

    var nodes: HashMap<String, Node> = HashMap()
    var publisher: ZMQ.Socket = context.createSocket(ZMQ.PUB)

    init {
        publisher.bind(address)
    }

    fun publish() {
        val bos = ByteArrayOutputStream()
        val out = ObjectOutputStream(bos)

        nodes.forEach { guid, node ->
            try {
                out.reset()
                out.writeObject(node)
                out.flush()

                val payload = bos.toByteArray()
                publisher.sendMore(guid)
                publisher.sendMore(payload.size.toString())
                publisher.send(bos.toByteArray())
                logger.trace("Sending ${node.name} with length ${payload.size}")
            } catch(e: IOException) {
                logger.warn("in ${node.name}: ${e}")
            }
        }
    }

    fun close() {
        context.destroySocket(publisher)
    }
}
