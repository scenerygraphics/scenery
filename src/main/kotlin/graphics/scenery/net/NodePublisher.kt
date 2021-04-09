package graphics.scenery.net

import org.joml.Matrix4f
import org.joml.Vector3f
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Output
import com.jogamp.opengl.math.Quaternion
import de.javakaffee.kryoserializers.UUIDSerializer
import graphics.scenery.*
import graphics.scenery.serialization.*
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.Statistics
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.VolumeManager
import net.imglib2.img.basictypeaccess.array.ByteArray
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater

/**
 * Created by ulrik on 4/4/2017.
 */

class NodePublisher(override var hub: Hub?, val address: String = "tcp://127.0.0.1:6666", val context: ZContext = ZContext(4)): Hubable {
    private val logger by LazyLogger()

    var nodes: ConcurrentHashMap<Int, Node> = ConcurrentHashMap()
    private var publisher: ZMQ.Socket = context.createSocket(ZMQ.PUB)
    val kryo = freeze()
    var port: Int = try {
        publisher.bind(address)
        address.substringAfterLast(":").toInt()
    } catch (e: ZMQException) {
        logger.warn("Binding failed, trying random port: $e")
        publisher.bindToRandomPort(address.substringBeforeLast(":"))
    }

    fun publish() {
        nodes.forEach { (guid, node) ->
            // TODO: This needs to be removed in order for volume property sync to work, but currently it makes Kryo rather unhappy.
            if(node is Volume || node is Protein || node is RibbonDiagram) {
                return@forEach
            }
            val start = System.nanoTime()
            try {
                val bos = ByteArrayOutputStream()
                val output = Output(bos)
                kryo.writeClassAndObject(output, node)
                output.flush()

                publisher.sendMore(guid.toString())
                publisher.send(bos.toByteArray())
                Thread.sleep(1)
//                logger.info("Sending ${node.name} with length ${payload.size}")

                output.close()
                bos.close()
            } catch(e: IOException) {
                logger.warn("in ${node.name}: ${e}")
            } catch(e: AssertionError) {
                logger.warn("assertion: ${node.name}: ${e}")
            }

            val duration = (System.nanoTime() - start).toFloat()
            (hub?.get(SceneryElement.Statistics) as Statistics).add("Serialise", duration)
        }

    }

    fun close() {
        context.destroySocket(publisher)
        context.close()
    }

    companion object {
        fun freeze(): Kryo {
            val kryo = Kryo()
            kryo.isRegistrationRequired = false
            kryo.references = true
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

            return kryo
        }
    }
}
