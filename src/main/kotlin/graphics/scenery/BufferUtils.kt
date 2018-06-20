package graphics.scenery

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
    companion object BufferUtils {
        private const val SIZE_FLOAT = java.lang.Float.BYTES
        private const val SIZE_INT = java.lang.Integer.BYTES

        /**
         * Allocates a new direct [FloatBuffer] with a capacity of [num] floats.
         */
        fun allocateFloat(num: Int): FloatBuffer {
            return ByteBuffer.allocateDirect(SIZE_FLOAT * num).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }

        /**
         * Allocates a new direct [FloatBuffer] with a capacity of [num] floats, fill it with the members
         * of [array] and returns the flipped buffer.
         */
        fun allocateFloatAndPut(array: FloatArray): FloatBuffer {
            val b = ByteBuffer.allocateDirect(SIZE_FLOAT * array.size).order(ByteOrder.nativeOrder()).asFloatBuffer()
            (b.put(array) as Buffer).flip()

            return b
        }

        /**
         * Allocates a new direct [IntBuffer] with a capacity of [num] ints.
         */
        fun allocateInt(num: Int): IntBuffer {
            return ByteBuffer.allocateDirect(SIZE_INT * num).order(ByteOrder.nativeOrder()).asIntBuffer()
        }

        /**
         * Allocates a new direct [IntBuffer] with a capacity of [num] ints, fill it with the members
         * of [array] and returns the flipped buffer.
         */
        fun allocateIntAndPut(array: IntArray): IntBuffer {
            val b = ByteBuffer.allocateDirect(SIZE_INT * array.size).order(ByteOrder.nativeOrder()).asIntBuffer()
            (b.put(array) as Buffer).flip()

            return b
        }

        /**
         * Allocates a new direct [ByteBuffer] with a capacity of [num] bytes.
         */
        fun allocateByte(num: Int): ByteBuffer {
            return ByteBuffer.allocateDirect(num).order(ByteOrder.nativeOrder())
        }

        /**
         * Allocates a new direct [ByteBuffer] with a capacity of [num] bytes, fill it with the members
         * of [array] and returns the flipped buffer.
         */
        fun allocateByteAndPut(array: ByteArray): ByteBuffer {
            val b = ByteBuffer.allocateDirect(array.size).order(ByteOrder.nativeOrder())
            b.put(array).flip()

            return b
        }
    }
}
