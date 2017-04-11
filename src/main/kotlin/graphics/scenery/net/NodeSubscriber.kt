package graphics.scenery.net

import cleargl.GLMatrix
import cleargl.GLVector
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.*
import graphics.scenery.utils.Statistics
import org.objenesis.strategy.StdInstantiatorStrategy
import org.slf4j.LoggerFactory
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.ByteArrayInputStream
import java.io.StreamCorruptedException
import java.util.*

/**
 * Created by ulrik on 4/4/2017.
 */
class NodeSubscriber(override var hub: Hub?, val address: String = "udp://localhost:6666", val context: ZContext) : Hubable {

    var logger = LoggerFactory.getLogger("NodeSubscriber")
    var nodes: HashMap<Int, Node> = HashMap()
    var subscriber: ZMQ.Socket = context.createSocket(ZMQ.SUB)
    val kryo = Kryo()

    init {
        subscriber.connect(address)
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL)

        kryo.register(GLMatrix::class.java)
        kryo.register(GLVector::class.java)
        kryo.register(Node::class.java)
        kryo.register(Camera::class.java)
        kryo.register(DetachedHeadCamera::class.java)
        kryo.register(Quaternion::class.java)

        kryo.instantiatorStrategy = StdInstantiatorStrategy()
    }

    fun process() {
        while (true) {
            var start = 0L
            var duration = 0L
            try {
                start = System.nanoTime()
                val id = subscriber.recvStr().toInt()

//                logger.info("Received $id for deserializiation")
                duration = (System.nanoTime() - start)
                val payload = subscriber.recv()

//                logger.info("Have: ${nodes.keys.joinToString(", ")}, payload: ${payload != null}")

                if (payload != null) {
                    nodes[id]?.let { node ->
//                        logger.info("Deserializing $node from payload ${payload.size}")
                        val bin = ByteArrayInputStream(payload)
                        val input = Input(bin)
                        val o = kryo.readClassAndObject(input) as Node

//                        logger.info("Deserialized ${o.name}")
                        if(o.name == node.name) {
                            node.position = o.position
                            node.rotation = o.rotation
                        }

                        input.close()
                        bin.close()
                    }
                }
            } catch(e: StreamCorruptedException) {
                logger.warn("Corrupted stream")
            }

            hub?.get<Statistics>(SceneryElement.Statistics)?.add("Deserialise", duration.toFloat())

        }
    }

    fun close() {
        context.destroySocket(subscriber)
    }
}
