package graphics.scenery.net

import cleargl.GLMatrix
import cleargl.GLVector
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.UnsafeInput
import com.esotericsoftware.minlog.Log
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.Node
import org.objenesis.strategy.StdInstantiatorStrategy
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
        while(true) {
            try {
                val id = subscriber.recvStr()
                val size = subscriber.recvStr()

                val payload = ByteArray(size.toInt())
                val msg = subscriber.recv(payload, 0, size.toInt(), 0)

                if(msg == -1) {
                    logger.error("Error on receiving for $id of size $size")
                    continue
                }

                if(msg == size.toInt()) {
                    nodes[id]?.let { node ->
                        val bin = ByteArrayInputStream(payload)
                        val input = UnsafeInput(bin)
                        val o = kryo.readClassAndObject(input) as Camera

                        node.position = o.position
                        node.rotation = o.rotation

                        input.close()
                        bin.close()
                    }
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
