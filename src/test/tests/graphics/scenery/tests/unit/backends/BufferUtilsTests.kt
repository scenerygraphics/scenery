package graphics.scenery.tests.unit.backends

import graphics.scenery.BufferUtils
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import java.nio.*
import kotlin.test.assertEquals


/**
 * Tests for [BufferUtils].
 *
 * @author Aryaman Gupta <aryaman1994@gmail.com>
 */

class BufferUtilsTests {
    private val logger by LazyLogger()

    @Test
    fun testAllocateFloat() {
        logger.info("Testing allocation of a new direct float buffer ...")
        val size = kotlin.random.Random.nextInt(1, 10000)
        val floatBuf = BufferUtils.allocateFloat(size)

        assertEquals(size, floatBuf.capacity(), "Float buffer capacity was expected to be $size, but is ${floatBuf.capacity()}")
        assertEquals (ByteOrder.nativeOrder(), floatBuf.order(), "Float buffer was expected to be in ${ByteOrder.nativeOrder()}, but is ${floatBuf.order()}")
    }

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

       //numCorrect records how many elements in the array and buffer match
        var numCorrect = 0
        floatArr.forEach {

            if(it == floatBuf[index])
                numCorrect ++

            index ++
        }
        assertEquals(size, numCorrect, "Float buffer is not identical to array it was initialized with")
    }

    @Test
    fun testAllocateInt() {
        logger.info("Testing allocation of a new direct int buffer ...")
        val size = kotlin.random.Random.nextInt(1, 10000)
        val intBuf = BufferUtils.allocateInt(size)

        assertEquals(size, intBuf.capacity(), "Int buffer capacity was expected to be $size, but is ${intBuf.capacity()}")
        assertEquals (ByteOrder.nativeOrder(), intBuf.order(), "Int buffer was expected to be in ${ByteOrder.nativeOrder()}, but is ${intBuf.order()}")
    }

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

        //numCorrect records how many elements in the array and buffer match
        var numCorrect = 0
        intArr.forEach {

            if(it == intBuf[index])
                numCorrect ++

            index ++
        }
        assertEquals(size, numCorrect, "Int buffer is not identical to array it was initialized with")
    }

    @Test
    fun testAllocateByte() {
        logger.info("Testing allocation of a new direct byte buffer ...")
        val size = kotlin.random.Random.nextInt(1, 10000)
        val byteBuf = BufferUtils.allocateByte(size)

        assertEquals(size, byteBuf.capacity(), "Byte buffer capacity was expected to be $size, but is ${byteBuf.capacity()}")
        assertEquals (ByteOrder.nativeOrder(), byteBuf.order(), "Byte buffer was expected to be in ${ByteOrder.nativeOrder()}, but is ${byteBuf.order()}")
    }

    @Test
    fun testAllocateByteAndPut() {
        logger.info("Testing allocation and initialization of a new direct byte buffer with elements of a given array ...")
        val size = kotlin.random.Random.nextInt(1, 10000)
        val byteArr = kotlin.random.Random.nextBytes(size)

        val byteBuf = BufferUtils.allocateByteAndPut(byteArr)

        assertEquals(size, byteBuf.capacity(), "Byte buffer capacity was expected to be $size, but is ${byteBuf.capacity()}")
        assertEquals (ByteOrder.nativeOrder(), byteBuf.order(), "Byte buffer was expected to be in ${ByteOrder.nativeOrder()}, but is ${byteBuf.order()}")

        var index = 0

        //numCorrect records how many elements in the array and buffer match
        var numCorrect = 0
        byteArr.forEach {

            if(it == byteBuf[index])
                numCorrect ++

            index ++
        }
        assertEquals(size, numCorrect, "Byte buffer is not identical to array it was initialized with")
    }

}
