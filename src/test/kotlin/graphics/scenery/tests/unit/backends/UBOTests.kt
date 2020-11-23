package graphics.scenery.tests.unit.backends

import org.joml.Matrix4f
import org.joml.Vector3f
import graphics.scenery.backends.UBO
import graphics.scenery.numerics.Random
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.VideoEncodingQuality
import graphics.scenery.utils.extensions.compare
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.toFloatArray
import org.joml.Vector4f
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lwjgl.system.MemoryUtil
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
        ubo.add("member2", { Vector3f(1.0f, 2.0f, 3.0f) })
        // Matrices as arrays of 4-vectors are 16 byte-aligned
        ubo.add("member3", { Matrix4f().identity() })

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

        // test getting the values as well
        assertEquals(1337, ubo.get("member1")?.invoke())
        assertEquals(Vector3f(1.0f, 2.0f, 3.0f), ubo.get("member2")?.invoke())
        assertTrue(Matrix4f().identity().compare(ubo.get("member3")?.invoke() as Matrix4f, true))
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

        assertEquals(1337, ubo.get("member1")?.invoke())
        assertEquals(2 * 1337, ubo.get("member2")?.invoke())
        assertEquals(3 * 1337, ubo.get("member3")?.invoke())

        assertEquals("member1, member2, member3", ubo.members())
    }

    private fun Boolean.toInt(): Int {
        return if(this) {
            1
        } else {
            0
        }
    }

    /**
     * Tests repeated UBO serialisation with caching of the member hashes
     */
    @Test fun testHashedSerialisation() {
        logger.info("Testing hashed UBO serialisation...")

        val ubo = UBO()

        assertEquals(0, ubo.getSize())
        val storage = MemoryUtil.memAlloc(144)
        assertFalse(ubo.populate(storage))
        val storageView = storage.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val defaultVector = Vector3f(1.0f, 2.0f, 3.0f)

        val floatMember = kotlin.random.Random.nextFloat()
        val booleanMember = kotlin.random.Random.nextBoolean()
        val shortMember = kotlin.random.Random.nextInt().toShort()
        val enumMember = VideoEncodingQuality.values().random()
        val doubleMember = kotlin.random.Random.nextDouble()

        val twoVector = Random.random2DVectorFromRange(0.0f, 1.0f)
        val fourVector = Random.random4DVectorFromRange(0.0f, 1.0f)

        // ints are 4 byte-aligned
        ubo.add("member1", { 1337 })
        // 3/4-vectors are 16 byte-aligned
        ubo.add("member2", { defaultVector })
        // Matrices as arrays of 4-vectors are 16 byte-aligned
        ubo.add("member3", { Matrix4f().identity() })
        // Float member, 4-aligned
        ubo.add("floatMember", { floatMember })
        // Boolean, stored as Int, 4-aligned
        ubo.add("booleanMember", { booleanMember })
        // short, 2-aligned
        ubo.add("shortMember", { shortMember })
        // enum member, as int, 4-aligned
        ubo.add("enumMember", { enumMember })
        // double member
        ubo.add("doubleMember", { doubleMember })
        // two-vector
        ubo.add("twoVector", { twoVector })
        // four-vector
        ubo.add("fourVector", { fourVector })

        assertEquals(144, ubo.getSize())
        ubo.populate(storage)

        fun verifyData(v: Vector3f = defaultVector) {
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

            assertEquals(floatMember, storageView.asFloatBuffer().get(24))
            assertEquals(booleanMember.toInt(), storageView.asIntBuffer().get(25))
            assertEquals(shortMember, storageView.asShortBuffer().get(52))
            assertEquals(enumMember.ordinal, storageView.asIntBuffer().get(27))
            assertEquals(doubleMember, storageView.asDoubleBuffer().get(14), 0.00000005)

            val tmpTwo = floatArrayOf(
                storageView.asFloatBuffer().get(30),
                storageView.asFloatBuffer().get(31))
            val tmpFour = floatArrayOf(
                storageView.asFloatBuffer().get(32),
                storageView.asFloatBuffer().get(33),
                storageView.asFloatBuffer().get(34),
                storageView.asFloatBuffer().get(35))

            assertArrayEquals(twoVector.toFloatArray().toTypedArray(), tmpTwo.toTypedArray())
            assertArrayEquals(fourVector.toFloatArray().toTypedArray(), tmpFour.toTypedArray())

            // storage buffer should be at it's end now
            assertEquals(144, storage.position())
        }

        verifyData(v = defaultVector)

        val originalHash = ubo.hash

        logger.info("+ Testing 10 hashed runs and checking buffer is not being updated ...")
        for(i in 0..10) {
            storage.flip()
            assertEquals(false, ubo.populate(storage))
            assertEquals(originalHash, ubo.hash)

            verifyData(v = defaultVector)
        }

        logger.info("+ Modifying UBO and repopulating ...")
        val newVector = defaultVector + Random.random3DVectorFromRange(-1.0f, 1.0f)
        ubo.add("member2", { newVector })
        storage.flip()
        ubo.populate(storage)

        verifyData(v = newVector)

        MemoryUtil.memFree(storage)
    }

    @Test
    fun testAddIfMissing() {
        logger.info("Testing adding members only if they do not exist yet ...")
        val ubo = UBO()
        ubo.add("member1", { Random.randomFromRange(0.0f, 1.0f) })
        ubo.add("member2", { Random.randomFromRange(0.0f, 1.0f) })
        ubo.add("member3", { Vector4f(1.0f) })

        ubo.addIfMissing("member1", { 1337 })
        assertNotEquals(1337, ubo.get("member1")?.invoke())

        ubo.addIfMissing("member4", { Matrix4f().identity() })
        assertTrue(Matrix4f().identity().compare(ubo.get("member4")?.invoke() as Matrix4f, false))
    }

    @Test
    fun testDebuggingRoutines() {
        logger.info("Testing UBO debugging routines ...")
        val ubo = UBO()
        ubo.add("member1", { Random.randomFromRange(0.0f, 1.0f) })
        ubo.add("member2", { Random.randomFromRange(0.0f, 1.0f) })
        ubo.add("member3", { Random.randomFromRange(0.0f, 1.0f) })
        ubo.add("member4", { Matrix4f().identity() })
        ubo.add("member5", { Vector4f(0.0f) })

        val members = ubo.members()
        val membersAndContent = ubo.membersAndContent()

        assertEquals("member1, member2, member3, member4, member5", members)
        assertTrue(membersAndContent.contains("1.000E+0  0.000E+0  0.000E+0  0.000E+0\n" +
            " 0.000E+0  1.000E+0  0.000E+0  0.000E+0\n" +
            " 0.000E+0  0.000E+0  1.000E+0  0.000E+0\n" +
            " 0.000E+0  0.000E+0  0.000E+0  1.000E+0"))
        assertTrue(membersAndContent.contains("( 0.000E+0  0.000E+0  0.000E+0  0.000E+0)"))
        assertTrue(membersAndContent.contains("member1"))
        assertTrue(membersAndContent.contains("member2"))
        assertTrue(membersAndContent.contains("member3"))
        assertTrue(membersAndContent.contains("member4"))
        assertTrue(membersAndContent.contains("member5"))

        val hashes = ubo.perMemberHashes()
        assertTrue(hashes.isNotEmpty())
    }
}
