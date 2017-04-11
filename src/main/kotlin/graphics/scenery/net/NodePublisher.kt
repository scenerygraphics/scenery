package graphics.scenery.net

import cleargl.GLMatrix
import cleargl.GLVector
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.UnsafeMemoryOutput
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.*
import graphics.scenery.utils.Statistics
import org.slf4j.LoggerFactory
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * Created by ulrik on 4/4/2017.
 */

class NodePublisher(override var hub: Hub?, val address: String = "tcp://*:6666", val context: ZContext): Hubable {
    var logger = LoggerFactory.getLogger("NodePublisher")

    var nodes: HashMap<Int, Node> = HashMap()
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
        val idBuf = ByteBuffer.allocate(4)

        nodes.forEach { guid, node ->
            val start = System.nanoTime()
            try {
                val bos = ByteArrayOutputStream()
                val output = UnsafeMemoryOutput(bos)
                kryo.writeClassAndObject(output, node)
                output.flush()

                val payload = bos.toByteArray()
                idBuf.putInt(0, guid)
                publisher.sendMore(idBuf.array())
                publisher.send(bos.toByteArray())
                logger.info("Sending ${node.name} with length ${payload.size}")

                output.close()
                bos.close()
            } catch(e: IOException) {
                logger.warn("in ${node.name}: ${e}")
            }

            val duration = (System.nanoTime() - start).toFloat()
            (hub?.get(SceneryElement.Statistics) as Statistics).add("Serialise", duration)
        }

    }

    fun close() {
        context.destroySocket(publisher)
    }
}
