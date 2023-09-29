package graphics.scenery.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.utils.lazyLogger
import java.nio.ByteBuffer

class ByteBufferSerializer: Serializer<ByteBuffer>() {
    val logger by lazyLogger()

    override fun write(kryo: Kryo, output: Output, buffer: ByteBuffer) {
        kryo.writeClass(output, buffer.javaClass)
        val view = buffer.duplicate()
        output.writeInt(view.remaining())
        output.writeBoolean(buffer.isDirect)
        val tmp = ByteArray(4096)
        var bytes = 0

        while(view.hasRemaining()) {
            val size = minOf(view.remaining(), tmp.size)
            view.get(tmp, 0, size)
            output.writeBytes(tmp, 0, size)
            bytes += size
        }

//        logger.info("Serialised $bytes bytes for $buffer")
    }

    override fun read(kryo: Kryo, input: Input, oobClass: Class<out ByteBuffer>): ByteBuffer {
        val type = kryo.readClass(input).type
        val size = input.readInt()
        val isDirect = input.readBoolean()
        val buffer = if(isDirect) {
            ByteBuffer.allocateDirect(size)
        } else {
            ByteBuffer.allocate(size)
        }

        val view = buffer.duplicate()
        var bytes = 0
        val tmp = ByteArray(4096)

        while(view.hasRemaining()) {
            val size = minOf(view.remaining(), tmp.size)
            input.readBytes(tmp, 0, size)
            view.put(tmp, 0, size)
            bytes += size
        }

//        logger.info("Deserialised $bytes bytes for $buffer")

        return buffer
    }

    init {
        // TODO: Should this be true or false?
        isImmutable = false
    }
}
