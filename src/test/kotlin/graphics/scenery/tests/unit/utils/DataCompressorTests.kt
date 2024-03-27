package graphics.scenery.tests.unit.utils

import graphics.scenery.utils.DataCompressor
import graphics.scenery.utils.lazyLogger
import org.junit.Test
import org.lwjgl.system.MemoryUtil
import java.util.*
import kotlin.test.assertTrue

/**
 * Tests how [DataCompressor] can be used for lossless compression and decompression of binary data.
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de>
 */
class DataCompressorTests {
    private val logger by lazyLogger()

    /**
     * Tests compression and decompression of data.
     */
    @Test
    fun testCompressionDecompression() {
        val dataSize = 1024*1024*8
        val buffer = MemoryUtil.memAlloc(dataSize)

        // insert random integers between 0 and 5 into the buffer
        val rd = Random()
        val intBuffer = buffer.asIntBuffer()
        for(i in 0 until intBuffer.remaining()) {
            intBuffer.put(rd.nextInt(5))
        }

        val compressionTool = DataCompressor.CompressionTool.LZ4
        val compressor = DataCompressor(compressionTool)

        val maxDecompressedSize = compressor.returnCompressBound(buffer.remaining().toLong())
        val compressedBuffer = MemoryUtil.memAlloc(maxDecompressedSize)

        val compressedLength = compressor.compress(compressedBuffer, buffer, 3)
        compressedBuffer.limit(compressedLength.toInt())

        val compressionRatio = compressedLength.toFloat() / dataSize.toFloat()
        logger.info("Length of compressed buffer: $compressedLength and compression ration is: $compressionRatio")

        val decompressed = MemoryUtil.memAlloc(dataSize)
        compressor.decompress(decompressed, compressedBuffer)

        val successful = compressor.verifyDecompressed(buffer, decompressed)
        if(successful) {
            logger.info("The buffer was found to be compressed and decompressed successfully")
        } else {
            logger.info("Compression and decompression was not successful")
        }

        assertTrue {
            compressor.verifyDecompressed(buffer, decompressed)
        }

        MemoryUtil.memFree(buffer)
        MemoryUtil.memFree(decompressed)
    }
}