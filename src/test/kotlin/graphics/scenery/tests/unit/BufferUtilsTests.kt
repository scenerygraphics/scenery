package graphics.scenery.tests.unit

import graphics.scenery.BufferUtils
import graphics.scenery.utils.lazyLogger
import org.junit.Test
import java.nio.ByteOrder
import kotlin.test.assertEquals


/**
 * Tests for [BufferUtils].
 *
 * @author Aryaman Gupta <aryaman1994@gmail.com>
 */

class BufferUtilsTests {
    private val logger by lazyLogger()

    /**
     * Tests allocation of a Float buffer
     */
    @Test
    fun testAllocateFloat() {
        logger.info("Testing allocation of a new direct float buffer ...")
        val size = kotlin.random.Random.nextInt(1, 10000)
        val floatBuf = BufferUtils.allocateFloat(size)

        assertEquals(size, floatBuf.capacity(), "Float buffer capacity was expected to be $size, but is ${floatBuf.capacity()}")
        assertEquals (ByteOrder.nativeOrder(), floatBuf.order(), "Float buffer was expected to be in ${ByteOrder.nativeOrder()}, but is ${floatBuf.order()}")
    }

    /**
     * Tests allocation of a float buffer and filling it from a pre-existing array.
     */
    @Test
    fun testAllocateFloatAndPut() {
        logger.info("Testing allocation and initialization of a new direct float buffer with elements of a given array ...")
        val size = kotlin.random.Random.nextInt(1, 10000)
        val floatArr = FloatArray(size)

        for(i in 0..size-1) {
            floatArr[i] = kotlin.random.Random.nextDouble(-10000.0, 10000.0).toFloat()
        }

        val floatBuf = BufferUtils.allocateFloatAndPut(floatArr)

        assertEquals(size, floatBuf.capacity(), "Float buffer capacity was expected to be $size, but is ${floatBuf.capacity()}")
        assertEquals (ByteOrder.nativeOrder(), floatBuf.order(), "Float buffer was expected to be in ${ByteOrder.nativeOrder()}, but is ${floatBuf.order()}")

        var index = 0
        floatArr.forEach {
            assertEquals(it, floatArr[index], "Int buffer is not identical to array it was initialized with")
            index ++
        }
    }

    /**
     * Tests allocation of an int buffer.
     */
    @Test
    fun testAllocateInt() {
        logger.info("Testing allocation of a new direct int buffer ...")
        val size = kotlin.random.Random.nextInt(1, 10000)
        val intBuf = BufferUtils.allocateInt(size)

        assertEquals(size, intBuf.capacity(), "Int buffer capacity was expected to be $size, but is ${intBuf.capacity()}")
        assertEquals (ByteOrder.nativeOrder(), intBuf.order(), "Int buffer was expected to be in ${ByteOrder.nativeOrder()}, but is ${intBuf.order()}")
    }

    /**
     * Tests allocation of an int buffer and filling it from a pre-existing array.
     */
    @Test
    fun testAllocateIntAndPut() {
        logger.info("Testing allocation and initialization of a new direct int buffer with elements of a given array ...")
        val size = kotlin.random.Random.nextInt(1, 10000)
        val intArr = IntArray(size)

        for(i in 0..size-1) {
            intArr[i] = kotlin.random.Random.nextInt(-10000, 10000)
        }

        val intBuf = BufferUtils.allocateIntAndPut(intArr)

        assertEquals(size, intBuf.capacity(), "Int buffer capacity was expected to be $size, but is ${intBuf.capacity()}")
        assertEquals (ByteOrder.nativeOrder(), intBuf.order(), "Int buffer was expected to be in ${ByteOrder.nativeOrder()}, but is ${intBuf.order()}")

        var index = 0
        intArr.forEach {
            assertEquals(it, intBuf[index], "Int buffer is not identical to array it was initialized with")
            index ++
        }
    }

    /**
     * Tests allocation of a byte buffer.
     */
    @Test
    fun testAllocateByte() {
        logger.info("Testing allocation of a new direct byte buffer ...")
        val size = kotlin.random.Random.nextInt(1, 10000)
        val byteBuf = BufferUtils.allocateByte(size)

        assertEquals(size, byteBuf.capacity(), "Byte buffer capacity was expected to be $size, but is ${byteBuf.capacity()}")
        assertEquals (ByteOrder.nativeOrder(), byteBuf.order(), "Byte buffer was expected to be in ${ByteOrder.nativeOrder()}, but is ${byteBuf.order()}")
    }

    /**
     * Tests allocation of a byte buffer and filling it from a pre-existing array.
     */
    @Test
    fun testAllocateByteAndPut() {
        logger.info("Testing allocation and initialization of a new direct byte buffer with elements of a given array ...")
        val size = kotlin.random.Random.nextInt(1, 10000)
        val byteArr = kotlin.random.Random.nextBytes(size)

        val byteBuf = BufferUtils.allocateByteAndPut(byteArr)

        assertEquals(size, byteBuf.capacity(), "Byte buffer capacity was expected to be $size, but is ${byteBuf.capacity()}")
        assertEquals(ByteOrder.nativeOrder(), byteBuf.order(), "Byte buffer was expected to be in ${ByteOrder.nativeOrder()}, but is ${byteBuf.order()}")

        var index = 0
        byteArr.forEach {
            assertEquals(it, byteArr[index], "Int buffer is not identical to array it was initialized with")
            index ++
        }
    }
}
