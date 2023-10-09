package graphics.scenery.utils

import org.xerial.snappy.Snappy
import java.nio.ByteBuffer
import org.lwjgl.util.zstd.Zstd.*
import org.lwjgl.util.zstd.ZstdX.ZSTD_findDecompressedSize
import org.lwjgl.util.lz4.LZ4.*
import org.lwjgl.util.lz4.LZ4Frame.LZ4F_getErrorName
import org.lwjgl.util.lz4.LZ4Frame.LZ4F_isError

class DataCompressor {

    enum class CompressionTool {
        ZSTD,
        LZ4,
        Snappy
    }

    fun compressSnappy(compressed: ByteBuffer, uncompressed: ByteBuffer): Long {
        return Snappy.compress(uncompressed, compressed).toLong()
    }

    fun decompressSnappy(decompressed: ByteBuffer, compressed: ByteBuffer): Long {
        return Snappy.uncompress(compressed, decompressed).toLong()
    }

    fun compressZSTD(compressed: ByteBuffer, uncompressed: ByteBuffer, level: Int): Long {
        return checkZSTD(ZSTD_compress(compressed, uncompressed, level))
    }

    fun decompressZSTD(decompressed: ByteBuffer, compressed: ByteBuffer): Long {
        checkZSTD(ZSTD_findDecompressedSize(compressed))
        return ZSTD_decompress(decompressed, compressed)
    }

    fun compressLZ4(compressed: ByteBuffer, uncompressed: ByteBuffer, level: Int): Long {
        return checkLZ4F(LZ4_compress_fast(uncompressed, compressed, level).toLong())
    }

    fun decompressLZ4(decompressed: ByteBuffer, compressed: ByteBuffer): Long {
        return checkLZ4F(LZ4_decompress_safe(compressed, decompressed).toLong())
    }

    private fun checkZSTD(errorCode: Long): Long {
        check(!ZSTD_isError(errorCode)) { "Zstd error: " + errorCode + " | " + ZSTD_getErrorName(errorCode) }
        return errorCode
    }

    private fun checkLZ4F(errorCode: Long): Long {
        check(!LZ4F_isError(errorCode)) { "LZ4 error: " + errorCode + " | " + LZ4F_getErrorName(errorCode) }
        return errorCode
    }

    fun returnCompressBound(sourceSize: Long, compressionTool: CompressionTool): Int {
        return when (compressionTool) {
            CompressionTool.ZSTD -> {
                ZSTD_COMPRESSBOUND(sourceSize).toInt()
            }
            CompressionTool.LZ4 -> {
                LZ4_compressBound(sourceSize.toInt())
            }
            CompressionTool.Snappy -> {
                Snappy.maxCompressedLength(sourceSize.toInt())
            }
        }
    }

    fun verifyDecompressed(uncompressed: ByteBuffer, decompressed: ByteBuffer) {
        val decompressedSize = decompressed.remaining().toLong()
        check(
            decompressedSize == uncompressed.remaining().toLong()
        ) {
            String.format(
                "Decompressed size %d != uncompressed size %d",
                decompressedSize,
                uncompressed.remaining()
            )
        }
        println("uncompressed size: ${uncompressed.remaining()} and decompressed size: $decompressedSize")
        for (i in 0 until uncompressed.remaining()) {
            check(decompressed[i] == uncompressed[i]) { "Decompressed != uncompressed at: $i" }
        }
    }

    fun compress(compressed: ByteBuffer, uncompressed: ByteBuffer, level: Int, compressionTool: CompressionTool): Long {
        return if (compressionTool == CompressionTool.ZSTD) {
            compressZSTD(compressed, uncompressed, level)
        } else if (compressionTool == CompressionTool.LZ4) {
            compressLZ4(compressed, uncompressed, level)
        } else if (compressionTool == CompressionTool.Snappy) {
            compressSnappy(compressed, uncompressed)
        } else {
            -1
        }
    }

    fun decompress(decompressed: ByteBuffer, compressed: ByteBuffer, compressionTool: CompressionTool): Long {
        return if(compressionTool == CompressionTool.ZSTD) {
            decompressZSTD(decompressed, compressed)
        } else if(compressionTool == CompressionTool.LZ4) {
            decompressLZ4(decompressed, compressed)
        } else if (compressionTool == CompressionTool.Snappy) {
            decompressSnappy(decompressed, compressed)
        } else {
            -1
        }
    }
}
