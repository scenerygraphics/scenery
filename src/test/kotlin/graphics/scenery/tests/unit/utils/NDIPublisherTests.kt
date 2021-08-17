package graphics.scenery.tests.unit.utils

import com.sun.jna.Memory
import graphics.scenery.Hub
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.NDI
import graphics.scenery.utils.NDIPublisher
import org.junit.Assume.assumeNoException
import org.junit.BeforeClass
import org.junit.Test

class NDIPublisherTests {
    val logger by LazyLogger()

    private val hub = Hub()
    private val frameCount = 600

    companion object {
        val logger by LazyLogger()

        @JvmStatic @BeforeClass
        fun checkForNDIRuntime() {
            try {
                NDI.instance.NDIlib_initialize()
                NDI.instance.NDIlib_destroy()
            } catch (e: UnsatisfiedLinkError) {
                logger.warn("NDI runtime not available, skipping tests.")
                assumeNoException(e)
            } catch (e: NoClassDefFoundError) {
                logger.warn("NDI runtime not available, skipping tests.")
                assumeNoException(e)
            }
        }
    }

    @Test
    fun testInitialisation() {
        logger.info("Creating NDI Publisher...")
        val pub = NDIPublisher(512, 512, 60, hub)
        logger.info("Closing NDI Publisher...")
        pub.close()
        logger.info("NDI publisher closed.")
    }

    @Test
    fun testFrameSendingCopy() {
        logger.info("Creating NDI Publisher...")
        val pub = NDIPublisher(512, 512, 60, hub)

        repeat(frameCount) { frame ->
            logger.debug("Sending frame $frame")
            val pixels = ByteArray(pub.frameWidth * pub.frameHeight * 4) {
                when {
                    it % 4 == 0 -> 255.toByte()
                    else -> ((it - frame) % 255).toByte()
                }
            }

            pub.sendFrame(pixels)
        }

        logger.info("Sent $frameCount frames with copying.")

        logger.info("Closing NDI Publisher...")
        pub.close()
        logger.info("NDI publisher closed.")
    }

    @Test
    fun testFrameSendingInPlace() {
        logger.info("Creating NDI Publisher...")
        val pub = NDIPublisher(512, 512, 60, hub)
        val memory = Memory(pub.frameWidth * pub.frameHeight * 4L)

        repeat(frameCount) { frame ->
            logger.debug("Sending frame $frame")
            val pixels = ByteArray(pub.frameWidth * pub.frameHeight * 4) {
                when {
                    it % 4 == 0 -> 255.toByte()
                    else -> ((it + frame) % 255).toByte()
                }
            }
            memory.write(0, pixels, 0, pixels.size)

            pub.sendFrame(memory)
        }

        logger.info("Sent $frameCount in-place frames.")

        logger.info("Closing NDI Publisher...")
        pub.close()
        logger.info("NDI publisher closed.")
    }
}
