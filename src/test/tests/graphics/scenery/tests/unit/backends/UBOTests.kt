package graphics.scenery.tests.unit.backends

import cleargl.GLMatrix
import cleargl.GLVector
import graphics.scenery.backends.UBO
import graphics.scenery.numerics.Random
import graphics.scenery.utils.LazyLogger
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lwjgl.system.MemoryUtil
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Tests for [UBO] serialisation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class UBOTests {
    private val logger by LazyLogger()

    /**
     * Tests UBO serialisation in-order of the members
     */
    @Test
    fun testInOrderSerialisation() {
        logger.info("Testing in-order UBO serialisation...")

        val ubo = UBO()
        val storage = MemoryUtil.memAlloc(96)
        val storageView = storage.duplicate().order(ByteOrder.LITTLE_ENDIAN)

        // ints are 4 byte-aligned
        ubo.add("member1", { 1337 })
        // 3/4-vectors are 16 byte-aligned
        ubo.add("member2", { GLVector(1.0f, 2.0f, 3.0f) })
        // Matrices as arrays of 4-vectors are 16 byte-aligned
        ubo.add("member3", { GLMatrix.getIdentity() })

        ubo.populate(storage)

        // 1337 is expected at byte 0
        assertEquals(1337, storageView.asIntBuffer().get())
        // 1.0f is expected at byte 16
        assertEquals(1.0f, storageView.asFloatBuffer().get(4))
        // 2.0f is expected at byte 17
        assertEquals(2.0f, storageView.asFloatBuffer().get(5))
        // 2.0f is expected at byte 18
        assertEquals(3.0f, storageView.asFloatBuffer().get(6))

        // matrix starts at offset 32
        val array = FloatArray(16)
        (storageView.asFloatBuffer().position(32 / 4) as FloatBuffer).get(array)
        assertEquals(4.0f, array.sum())

        // storage buffer should be at it's end now
        assertEquals(96, storage.position())

        MemoryUtil.memFree(storage)
    }

    /**
     * Tests out-of-order UBO serialisation
     */
    @Test
    fun testOutOfOrderSerialisation() {
        logger.info("Testing out-of-order UBO serialisation...")

        val ubo = UBO()
        val storage = MemoryUtil.memAlloc(12)
        val storageView = storage.duplicate().order(ByteOrder.LITTLE_ENDIAN)

        ubo.add("member1", { 1337 }, offset = 4 * 2)
        ubo.add("member2", { 2 * 1337 }, offset = 4 * 0)
        ubo.add("member3", { 3 * 1337 }, offset = 4 * 1)

        ubo.populate(storage)

        // 1337 is expected at byte 0
        assertEquals(1337, storageView.asIntBuffer().get(2))
        assertEquals(2 * 1337, storageView.asIntBuffer().get(0))
        assertEquals(3 * 1337, storageView.asIntBuffer().get(1))
        // 1.0f is expected at byte 16

        // storage buffer should be at it's end now
        assertEquals(storage.capacity(), storage.position())

        MemoryUtil.memFree(storage)
    }

    /**
     * Tests repeated UBO serialisation with caching of the member hashes
     */
    @Test fun testHashedSerialisation() {
        logger.info("Testing hashed UBO serialisation...")

        val ubo = UBO()
        val storage = MemoryUtil.memAlloc(96)
        val storageView = storage.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val defaultVector = GLVector(1.0f, 2.0f, 3.0f)

        // ints are 4 byte-aligned
        ubo.add("member1", { 1337 })
        // 3/4-vectors are 16 byte-aligned
        ubo.add("member2", { defaultVector })
        // Matrices as arrays of 4-vectors are 16 byte-aligned
        ubo.add("member3", { GLMatrix.getIdentity() })

        ubo.populate(storage)

        fun verifyData(v: GLVector = defaultVector) {
            // 1337 is expected at byte 0
            assertEquals(1337, storageView.asIntBuffer().get())
            // 1.0f is expected at byte 16
            assertEquals(v.x(), storageView.asFloatBuffer().get(4))
            // 2.0f is expected at byte 17
            assertEquals(v.y(), storageView.asFloatBuffer().get(5))
            // 2.0f is expected at byte 18
            assertEquals(v.z(), storageView.asFloatBuffer().get(6))

            // matrix starts at offset 32
            val array = FloatArray(16)
            (storageView.asFloatBuffer().position(32 / 4) as FloatBuffer).get(array)
            assertEquals(4.0f, array.sum())

            // storage buffer should be at it's end now
            assertEquals(96, storage.position())
        }

        verifyData(v = defaultVector)

        logger.info("+ Testing 10 hashed runs and checking buffer is not being updated ...")
        for(i in 0..10) {
            storage.flip()
            assertEquals(false, ubo.populate(storage))

            verifyData(v = defaultVector)
        }

        logger.info("+ Modifying UBO and repopulating ...")
        val newVector = defaultVector + Random.randomVectorFromRange(3, -1.0f, 1.0f)
        ubo.add("member2", { newVector })
        storage.flip()
        ubo.populate(storage)

        verifyData(v = newVector)

        MemoryUtil.memFree(storage)
    }
}
