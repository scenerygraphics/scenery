package graphics.scenery

import org.lwjgl.system.MemoryUtil
import java.nio.*

/**
 * Buffer utilities class
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BufferUtils {

    /**
     * Buffer utilities companion class, for allocating various kinds of buffers and filling them in one go.
     */
    companion object {
        private const val SIZE_FLOAT = java.lang.Float.BYTES
        private const val SIZE_INT = java.lang.Integer.BYTES

        /**
         * Allocates a new direct [FloatBuffer] with a capacity of [num] floats.
         */
        @JvmStatic fun allocateFloat(num: Int): FloatBuffer {
            return MemoryUtil.memAlloc(SIZE_FLOAT * num).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }

        /**
         * Allocates a new direct [FloatBuffer] with a capacity to fit [array], and fills it with the members
         * of [array] and returns the flipped buffer.
         */
        @JvmStatic fun allocateFloatAndPut(array: FloatArray): FloatBuffer {
            val b = MemoryUtil.memAlloc(SIZE_FLOAT * array.size).order(ByteOrder.nativeOrder()).asFloatBuffer()
            (b.put(array) as Buffer).flip()

            return b
        }

        /**
         * Allocates a new direct [IntBuffer] with a capacity of [num] ints.
         */
        @JvmStatic fun allocateInt(num: Int): IntBuffer {
            return MemoryUtil.memAlloc(SIZE_INT * num).order(ByteOrder.nativeOrder()).asIntBuffer()
        }

        /**
         * Allocates a new direct [IntBuffer] with a capacity to fit [array], and fills it with the members
         * of [array] and returns the flipped buffer.
         */
        @JvmStatic fun allocateIntAndPut(array: IntArray): IntBuffer {
            val b = MemoryUtil.memAlloc(SIZE_INT * array.size).order(ByteOrder.nativeOrder()).asIntBuffer()
            (b.put(array) as Buffer).flip()

            return b
        }

        /**
         * Allocates a new direct [ByteBuffer] with a capacity of [num] bytes.
         */
        @JvmStatic fun allocateByte(num: Int): ByteBuffer {
            return MemoryUtil.memAlloc(num).order(ByteOrder.nativeOrder())
        }

        /**
         * Allocates a new direct [ByteBuffer] with a capacity to fit [array], and fills it with the members
         * of [array] and returns the flipped buffer.
         */
        @JvmStatic fun allocateByteAndPut(array: ByteArray): ByteBuffer {
            val b = MemoryUtil.memAlloc(array.size).order(ByteOrder.nativeOrder())
            b.put(array).flip()

            return b
        }
    }
}
