package graphics.scenery.volumes.vdi

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.serialization.ByteBufferSerializer
import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

class VDIDataIO {
    companion object {

        private fun freeze(kryo: Kryo?): Kryo {
            val b = ByteBuffer.allocateDirect(1)
            return if(kryo == null) {
                val temp = Kryo()
                temp.register(VDIData::class.java)
//                temp.register(ByteBuffer::class.java, ByteBufferSerializer())
//                temp.register(b.javaClass, ByteBufferSerializer())
                temp.register(VDIBufferSizes::class.java)
                temp.register(VDIMetadata::class.java)
                temp.register(Matrix4f::class.java)
                temp.register(Vector3f::class.java)
                temp.register(Vector2i::class.java)
                temp
            } else {
                kryo
            }
        }

        @JvmStatic
        fun read(from: InputStream, kryo: Kryo? = null): VDIData {
            val k = freeze(kryo)
            val input = Input(from)
            val read = k.readClassAndObject(input)
            input.close()
            return read as VDIData
        }

        @JvmStatic
        fun write(vdiData: VDIData, to: OutputStream, kryo: Kryo? = null): Long {
            val k = freeze(kryo)
            val output = Output(to)
            k.writeClassAndObject(output, vdiData)
            val total = output.total()
            output.close()
            return total
        }
    }
}
