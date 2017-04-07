package graphics.scenery.net

import cleargl.GLMatrix
import cleargl.GLVector
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.io.UnsafeOutput
import com.esotericsoftware.minlog.Log
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
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

    val kryo = Kryo()

    init {
        publisher.bind(address)

        kryo.register(GLMatrix::class.java)
        kryo.register(GLVector::class.java)
        kryo.register(Node::class.java)
        kryo.register(Camera::class.java)
        kryo.register(DetachedHeadCamera::class.java)
        kryo.register(Quaternion::class.java)
    }

    fun publish() {
        val bos = ByteArrayOutputStream()
        val output = UnsafeOutput(bos)
        output.flush()

        nodes.forEach { guid, node ->
            try {
                kryo.writeClassAndObject(output, node)
                output.flush()

                val payload = bos.toByteArray()
                publisher.sendMore(guid)
                publisher.sendMore(payload.size.toString())
                publisher.send(bos.toByteArray())
                logger.trace("Sending ${node.name} with length ${payload.size}")
            } catch(e: IOException) {
                logger.warn("in ${node.name}: ${e}")
            }
        }

        output.close()
    }

    fun close() {
        context.destroySocket(publisher)
    }
}
