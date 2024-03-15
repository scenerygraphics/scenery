package graphics.scenery.tests.examples.advanced

import graphics.scenery.SceneryBase
import graphics.scenery.utils.DataCompressor
import org.lwjgl.system.MemoryUtil
import java.util.Random
import kotlin.test.assertTrue

/**
 * Example to show how [DataCompressor] can be used for lossless compression and decompression of binary data.
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */
class DataCompressionExample: SceneryBase("DataCompressionExample", wantREPL = false) {

    override fun main() {

        val dataSize = 20000000
        val buffer = MemoryUtil.memAlloc((dataSize))

        //insert random integers between 0 and 5 into the buffer
        val rd = Random()
        val intBuffer = buffer.asIntBuffer()
        for (i in 0 until intBuffer.remaining()) {
            intBuffer.put(rd.nextInt(5))
        }

        val compressionTool = DataCompressor.CompressionTool.LZ4
        val compressor = DataCompressor(compressionTool)

        val maxDecompressedSize = compressor.returnCompressBound(buffer.remaining().toLong())
        val compressedBuffer = MemoryUtil.memAlloc(maxDecompressedSize)

        val compressedLength = compressor.compress(compressedBuffer, buffer, 3)
        compressedBuffer.limit(compressedLength.toInt())

        val compressionRatio = compressedLength.toFloat()/dataSize.toFloat()
        logger.info("Length of compressed buffer: $compressedLength and compression ration is: $compressionRatio")

        val decompressed = MemoryUtil.memAlloc(dataSize)
        compressor.decompress(decompressed, compressedBuffer)

        val successful = compressor.verifyDecompressed(buffer, decompressed)
        if(successful) {
            logger.info("The buffer was found to be compressed and decompressed successfully")
        } else {
            logger.info("Compression and decompression was not successful")
        }

        // add assertions, these only get called when the example is called
        // as part of scenery's integration tests
        assertions[AssertionCheckPoint.AfterClose]?.add {
            assertTrue {
                compressor.verifyDecompressed(buffer, decompressed)
            }
        }
    }

    /**
     * Companion object for providing a main method.
     */
    companion object {
        /**
         * The main entry point. Executes this example application when it is called.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            DataCompressionExample().main()
        }
    }
}
