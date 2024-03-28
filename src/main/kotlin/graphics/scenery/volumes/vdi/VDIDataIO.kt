package graphics.scenery.volumes.vdi

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f
import java.io.InputStream
import java.io.OutputStream

/**
 * A utility class handling reading and writing of metadata for Volumetric Depth
 * Images (VDIs) ([VDIData]) with serialization handled by [Kryo].
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de> and Ulrik Günther <hello@ulrik.is>
 */
class VDIDataIO {
    companion object {

        private fun freeze(kryo: Kryo?): Kryo {
            return if(kryo == null) {
                val temp = Kryo()
                temp.register(VDIData::class.java)
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

        /**
         * Reads [VDIData] from a serialized [InputStream].
         *
         * @param[from] The serialized [InputStream] from which the [VDIData] is to be read.
         * @param[kryo] A [Kryo] instance with all required classes registered (see [freeze]). If null,
         * a new [Kryo] will be instantiated and registered with required classes using [freeze].
         *
         * @return The deserialized [VDIData].
         */
        @JvmStatic
        fun read(from: InputStream, kryo: Kryo? = null): VDIData {
            val k = freeze(kryo)
            val input = Input(from)
            val read = k.readClassAndObject(input)
            input.close()
            return read as VDIData
        }

        /**
         * Writes [VDIData] to a serialized [OutputStream].
         *
         * @param[vdiData] The [VDIData] to be serialized.
         * @param[to] The [OutputStream] to which the serialized [vdiData] is to be written.
         * @param[kryo] A [Kryo] instance with all required classes registered (see [freeze]). If null,
         * a new [Kryo] will be instantiated and registered with required classes using [freeze].
         *
         * @return The total number of bytes written to the [OutputStream].
         */
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
