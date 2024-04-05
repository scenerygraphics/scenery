package graphics.scenery.tests.examples.volumes.benchmarks

import graphics.scenery.utils.DataCompressor
import graphics.scenery.utils.lazyLogger
import graphics.scenery.volumes.vdi.benchmarks.BenchmarkSetup
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import kotlin.system.measureNanoTime

class VDICompressionBenchmarks(val dataset: BenchmarkSetup.Dataset, val windowWidth: Int, val windowHeight: Int, val numSupersegments: Int, val bufferType: BufferType) {
    private val logger by lazyLogger()

    enum class BufferType {
        Depth,
        Color
    }

    fun testCompressionDecompression() {

        val filePrefix = dataset.toString() + "_${windowWidth}_${windowHeight}_${numSupersegments}"

        var totalCompressionTime: Double = 0.0
        var totalDecompressionTime: Double = 0.0

        var totalCompressedLength: Long = 0

        var num = 0
        while (true) {

            val fileName = if(bufferType == BufferType.Color) {
                "${filePrefix}_${num}.vdi-col"
            } else {
                "${filePrefix}_${num}.vdi-depth"
            }

            try {
                FileInputStream(File(fileName))
            } catch(e: FileNotFoundException) {
                logger.info("File $fileName not found. Terminating benchmark.")
                break
            }

            val dataArray: ByteArray = File(fileName).readBytes()
            num++

            val compressionTool = DataCompressor.CompressionTool.LZ4
            val compressor = DataCompressor(compressionTool)

            val buffer = MemoryUtil.memCalloc(dataArray.size)
            buffer.put(dataArray).flip()

            val compressedBuffer: ByteBuffer
            val compressionTime = measureNanoTime {
                val maxDecompressedSize = compressor.returnCompressBound(buffer.remaining().toLong())
                compressedBuffer = MemoryUtil.memAlloc(maxDecompressedSize)

                val compressedLength = compressor.compress(compressedBuffer, buffer, 3)
                compressedBuffer.limit(compressedLength.toInt())

                totalCompressedLength += compressedLength

                val compressionRatio = compressedLength.toFloat() / dataArray.size.toFloat()

                logger.info("For file: $num, Compression ratio: $compressionRatio")
            }

            logger.info("For file: $num, Compression time: ${compressionTime/1e9} seconds")

            totalCompressionTime += (compressionTime/1e9)

            val decompressed: ByteBuffer
            val decompressionTime = measureNanoTime {
                decompressed = MemoryUtil.memAlloc(dataArray.size)
                compressor.decompress(decompressed, compressedBuffer)
            }
            logger.info("Decompression time was: ${decompressionTime/1e9} seconds")

            totalDecompressionTime += (decompressionTime/1e9)

            val successful = compressor.verifyDecompressed(buffer, decompressed)
            if(!successful) {
                logger.error("File: $num, compression and decompression were not successful!")
            }

            MemoryUtil.memFree(buffer)
            MemoryUtil.memFree(decompressed)
        }

        num -= 1

        logger.info("Average compression time: ${totalCompressionTime.toFloat()/num.toFloat()} seconds")
        logger.info("Average decompression time: ${totalDecompressionTime.toFloat()/num.toFloat()} seconds")
        logger.info("Average compressed length: ${(totalCompressedLength.toFloat()/num.toFloat()) / (1024*1024)} MiB")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VDICompressionBenchmarks(BenchmarkSetup.Dataset.Kingsnake, 1280, 720, 20, BufferType.Depth).testCompressionDecompression()
        }
    }
}
