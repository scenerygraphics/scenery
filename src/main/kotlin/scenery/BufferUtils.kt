import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BufferUtils {
    companion object BufferUtils {
        val SIZE_FLOAT = 4
        val SIZE_INT = 4

        fun allocateFloat(num: Int): FloatBuffer {
            return ByteBuffer.allocateDirect(SIZE_FLOAT*num).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }

        fun allocateFloatAndPut(array: FloatArray): FloatBuffer {
            val b = ByteBuffer.allocateDirect(SIZE_FLOAT*array.size).order(ByteOrder.nativeOrder()).asFloatBuffer()
            b.put(array).flip()

            return b
        }

        fun allocateInt(num: Int): IntBuffer {
            return ByteBuffer.allocateDirect(SIZE_INT*num).order(ByteOrder.nativeOrder()).asIntBuffer()
        }

        fun allocateIntAndPut(array: IntArray): IntBuffer {
            val b = ByteBuffer.allocateDirect(SIZE_INT*array.size).order(ByteOrder.nativeOrder()).asIntBuffer()
            b.put(array).flip()

            return b
        }

        fun allocateByteAndPut(array: ByteArray): ByteBuffer {
            val b = ByteBuffer.allocateDirect(array.size).order(ByteOrder.nativeOrder())
            b.put(array).flip()

            return b
        }
    }
}
