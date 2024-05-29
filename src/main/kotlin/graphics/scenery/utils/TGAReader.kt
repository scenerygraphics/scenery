package graphics.scenery.utils

import java.io.IOException

object TGAReader {
    val ARGB = Order(16, 8, 0, 24)
    val RGBA = Order(24, 16, 8, 0)
    val ABGR = Order(0, 8, 16, 24)
    fun getWidth(buffer: ByteArray): Int {
        return buffer[12].toInt() and 0xFF or (buffer[13].toInt() and 0xFF shl 8)
    }

    fun getHeight(buffer: ByteArray): Int {
        return buffer[14].toInt() and 0xFF or (buffer[15].toInt() and 0xFF shl 8)
    }

    @Throws(IOException::class)
    fun read(buffer: ByteArray, order: Order): IntArray {

        // header
        // int idFieldLength = buffer[0] & 0xFF;
        // int colormapType = buffer[1] & 0xFF;
        val type = buffer[2].toInt() and 0xFF
        val colormapOrigin = buffer[3].toInt() and 0xFF or (buffer[4].toInt() and 0xFF shl 8)
        val colormapLength = buffer[5].toInt() and 0xFF or (buffer[6].toInt() and 0xFF shl 8)
        val colormapDepth = buffer[7].toInt() and 0xFF
        // int originX = (buffer[8] & 0xFF) | (buffer[9] & 0xFF) << 8; //
        // unsupported
        // int originY = (buffer[10] & 0xFF) | (buffer[11] & 0xFF) << 8; //
        // unsupported
        val width = getWidth(buffer)
        val height = getHeight(buffer)
        val depth = buffer[16].toInt() and 0xFF
        val descriptor = buffer[17].toInt() and 0xFF
        var pixels: IntArray? = null
        pixels = when(type) {
            COLORMAP -> {
                val imageDataOffset = 18 + colormapDepth / 8 * colormapLength
                createPixelsFromColormap(
                    width, height, colormapDepth, buffer, imageDataOffset, buffer,
                    colormapOrigin, descriptor, order
                )
            }

            RGB -> createPixelsFromRGB(width, height, depth, buffer, 18, descriptor, order)
            GRAYSCALE -> createPixelsFromGrayscale(width, height, depth, buffer, 18, descriptor, order)
            COLORMAP_RLE -> {
                val imageDataOffset = 18 + colormapDepth / 8 * colormapLength
                val decodeBuffer = decodeRLE(width, height, depth, buffer, imageDataOffset)
                createPixelsFromColormap(
                    width, height, colormapDepth, decodeBuffer, 0, buffer, colormapOrigin,
                    descriptor, order
                )
            }

            RGB_RLE -> {
                val decodeBuffer = decodeRLE(width, height, depth, buffer, 18)
                createPixelsFromRGB(width, height, depth, decodeBuffer, 0, descriptor, order)
            }

            GRAYSCALE_RLE -> {
                val decodeBuffer = decodeRLE(width, height, depth, buffer, 18)
                createPixelsFromGrayscale(width, height, depth, decodeBuffer, 0, descriptor, order)
            }

            else -> throw IOException("Unsupported image type: $type")
        }
        return pixels
    }

    private const val COLORMAP = 1
    private const val RGB = 2
    private const val GRAYSCALE = 3
    private const val COLORMAP_RLE = 9
    private const val RGB_RLE = 10
    private const val GRAYSCALE_RLE = 11
    private const val RIGHT_ORIGIN = 0x10
    private const val UPPER_ORIGIN = 0x20
    private fun decodeRLE(
        width: Int, height: Int, depth: Int, buffer: ByteArray,
        offset: Int
    ): ByteArray {
        var offset = offset
        val elementCount = depth / 8
        val elements = ByteArray(elementCount)
        val decodeBufferLength = elementCount * width * height
        val decodeBuffer = ByteArray(decodeBufferLength)
        var decoded = 0
        while(decoded < decodeBufferLength) {
            val packet = buffer[offset++].toInt() and 0xFF
            if(packet and 0x80 != 0) { // RLE
                for(i in 0 until elementCount) {
                    elements[i] = buffer[offset++]
                }
                val count = (packet and 0x7F) + 1
                for(i in 0 until count) {
                    for(j in 0 until elementCount) {
                        decodeBuffer[decoded++] = elements[j]
                    }
                }
            } else { // RAW
                val count = (packet + 1) * elementCount
                for(i in 0 until count) {
                    decodeBuffer[decoded++] = buffer[offset++]
                }
            }
        }
        return decodeBuffer
    }

    @Throws(IOException::class)
    private fun createPixelsFromColormap(
        width: Int, height: Int, depth: Int,
        bytes: ByteArray, offset: Int, palette: ByteArray, colormapOrigin: Int, descriptor: Int,
        order: Order
    ): IntArray {
        var pixels: IntArray? = null
        val rs = order.redShift
        val gs = order.greenShift
        val bs = order.blueShift
        val `as` = order.alphaShift
        when(depth) {
            24 -> {
                pixels = IntArray(width * height)
                if(descriptor and RIGHT_ORIGIN != 0) {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val colormapIndex = bytes[offset + width * i + j].toInt() and 0xFF - colormapOrigin
                                var color = -0x1
                                if(colormapIndex >= 0) {
                                    val index = 3 * colormapIndex + 18
                                    val b = palette[index + 0].toInt() and 0xFF
                                    val g = palette[index + 1].toInt() and 0xFF
                                    val r = palette[index + 2].toInt() and 0xFF
                                    val a = 0xFF
                                    color = r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                }
                                pixels[width * i + (width - j - 1)] = color
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val colormapIndex = bytes[offset + width * i + j].toInt() and 0xFF - colormapOrigin
                                var color = -0x1
                                if(colormapIndex >= 0) {
                                    val index = 3 * colormapIndex + 18
                                    val b = palette[index + 0].toInt() and 0xFF
                                    val g = palette[index + 1].toInt() and 0xFF
                                    val r = palette[index + 2].toInt() and 0xFF
                                    val a = 0xFF
                                    color = r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                }
                                pixels[width * (height - i - 1) + (width - j - 1)] = color
                                j++
                            }
                            i++
                        }
                    }
                } else {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val colormapIndex = bytes[offset + width * i + j].toInt() and 0xFF - colormapOrigin
                                var color = -0x1
                                if(colormapIndex >= 0) {
                                    val index = 3 * colormapIndex + 18
                                    val b = palette[index + 0].toInt() and 0xFF
                                    val g = palette[index + 1].toInt() and 0xFF
                                    val r = palette[index + 2].toInt() and 0xFF
                                    val a = 0xFF
                                    color = r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                }
                                pixels[width * i + j] = color
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val colormapIndex = bytes[offset + width * i + j].toInt() and 0xFF - colormapOrigin
                                var color = -0x1
                                if(colormapIndex >= 0) {
                                    val index = 3 * colormapIndex + 18
                                    val b = palette[index + 0].toInt() and 0xFF
                                    val g = palette[index + 1].toInt() and 0xFF
                                    val r = palette[index + 2].toInt() and 0xFF
                                    val a = 0xFF
                                    color = r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                }
                                pixels[width * (height - i - 1) + j] = color
                                j++
                            }
                            i++
                        }
                    }
                }
            }

            32 -> {
                pixels = IntArray(width * height)
                if(descriptor and RIGHT_ORIGIN != 0) {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val colormapIndex = bytes[offset + width * i + j].toInt() and 0xFF - colormapOrigin
                                var color = -0x1
                                if(colormapIndex >= 0) {
                                    val index = 4 * colormapIndex + 18
                                    val b = palette[index + 0].toInt() and 0xFF
                                    val g = palette[index + 1].toInt() and 0xFF
                                    val r = palette[index + 2].toInt() and 0xFF
                                    val a = palette[index + 3].toInt() and 0xFF
                                    color = r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                }
                                pixels[width * i + (width - j - 1)] = color
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val colormapIndex = bytes[offset + width * i + j].toInt() and 0xFF - colormapOrigin
                                var color = -0x1
                                if(colormapIndex >= 0) {
                                    val index = 4 * colormapIndex + 18
                                    val b = palette[index + 0].toInt() and 0xFF
                                    val g = palette[index + 1].toInt() and 0xFF
                                    val r = palette[index + 2].toInt() and 0xFF
                                    val a = palette[index + 3].toInt() and 0xFF
                                    color = r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                }
                                pixels[width * (height - i - 1) + (width - j - 1)] = color
                                j++
                            }
                            i++
                        }
                    }
                } else {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val colormapIndex = bytes[offset + width * i + j].toInt() and 0xFF - colormapOrigin
                                var color = -0x1
                                if(colormapIndex >= 0) {
                                    val index = 4 * colormapIndex + 18
                                    val b = palette[index + 0].toInt() and 0xFF
                                    val g = palette[index + 1].toInt() and 0xFF
                                    val r = palette[index + 2].toInt() and 0xFF
                                    val a = palette[index + 3].toInt() and 0xFF
                                    color = r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                }
                                pixels[width * i + j] = color
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val colormapIndex = bytes[offset + width * i + j].toInt() and 0xFF - colormapOrigin
                                var color = -0x1
                                if(colormapIndex >= 0) {
                                    val index = 4 * colormapIndex + 18
                                    val b = palette[index + 0].toInt() and 0xFF
                                    val g = palette[index + 1].toInt() and 0xFF
                                    val r = palette[index + 2].toInt() and 0xFF
                                    val a = palette[index + 3].toInt() and 0xFF
                                    color = r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                }
                                pixels[width * (height - i - 1) + j] = color
                                j++
                            }
                            i++
                        }
                    }
                }
            }

            else -> throw IOException("Unsupported depth:$depth")
        }
        return pixels
    }

    @Throws(IOException::class)
    private fun createPixelsFromRGB(
        width: Int, height: Int, depth: Int, bytes: ByteArray,
        offset: Int, descriptor: Int, order: Order
    ): IntArray {
        var pixels: IntArray? = null
        val rs = order.redShift
        val gs = order.greenShift
        val bs = order.blueShift
        val `as` = order.alphaShift
        when(depth) {
            24 -> {
                pixels = IntArray(width * height)
                if(descriptor and RIGHT_ORIGIN != 0) {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val index = offset + 3 * width * i + 3 * j
                                val b = bytes[index + 0].toInt() and 0xFF
                                val g = bytes[index + 1].toInt() and 0xFF
                                val r = bytes[index + 2].toInt() and 0xFF
                                val a = 0xFF
                                pixels[width * i + (width - j - 1)] =
                                    r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val index = offset + 3 * width * i + 3 * j
                                val b = bytes[index + 0].toInt() and 0xFF
                                val g = bytes[index + 1].toInt() and 0xFF
                                val r = bytes[index + 2].toInt() and 0xFF
                                val a = 0xFF
                                pixels[width * (height - i - 1) + (width - j - 1)] =
                                    (r shl rs or (g shl gs) or (b shl bs)
                                            or (a shl `as`))
                                j++
                            }
                            i++
                        }
                    }
                } else {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val index = offset + 3 * width * i + 3 * j
                                val b = bytes[index + 0].toInt() and 0xFF
                                val g = bytes[index + 1].toInt() and 0xFF
                                val r = bytes[index + 2].toInt() and 0xFF
                                val a = 0xFF
                                pixels[width * i + j] = r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val index = offset + 3 * width * i + 3 * j
                                val b = bytes[index + 0].toInt() and 0xFF
                                val g = bytes[index + 1].toInt() and 0xFF
                                val r = bytes[index + 2].toInt() and 0xFF
                                val a = 0xFF
                                pixels[width * (height - i - 1) + j] =
                                    r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    }
                }
            }

            32 -> {
                pixels = IntArray(width * height)
                if(descriptor and RIGHT_ORIGIN != 0) {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val index = offset + 4 * width * i + 4 * j
                                val b = bytes[index + 0].toInt() and 0xFF
                                val g = bytes[index + 1].toInt() and 0xFF
                                val r = bytes[index + 2].toInt() and 0xFF
                                val a = bytes[index + 3].toInt() and 0xFF
                                pixels[width * i + (width - j - 1)] =
                                    r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val index = offset + 4 * width * i + 4 * j
                                val b = bytes[index + 0].toInt() and 0xFF
                                val g = bytes[index + 1].toInt() and 0xFF
                                val r = bytes[index + 2].toInt() and 0xFF
                                val a = bytes[index + 3].toInt() and 0xFF
                                pixels[width * (height - i - 1) + (width - j - 1)] =
                                    (r shl rs or (g shl gs) or (b shl bs)
                                            or (a shl `as`))
                                j++
                            }
                            i++
                        }
                    }
                } else {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val index = offset + 4 * width * i + 4 * j
                                val b = bytes[index + 0].toInt() and 0xFF
                                val g = bytes[index + 1].toInt() and 0xFF
                                val r = bytes[index + 2].toInt() and 0xFF
                                val a = bytes[index + 3].toInt() and 0xFF
                                pixels[width * i + j] = r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val index = offset + 4 * width * i + 4 * j
                                val b = bytes[index + 0].toInt() and 0xFF
                                val g = bytes[index + 1].toInt() and 0xFF
                                val r = bytes[index + 2].toInt() and 0xFF
                                val a = bytes[index + 3].toInt() and 0xFF
                                pixels[width * (height - i - 1) + j] =
                                    r shl rs or (g shl gs) or (b shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    }
                }
            }

            else -> throw IOException("Unsupported depth:$depth")
        }
        return pixels
    }

    @Throws(IOException::class)
    private fun createPixelsFromGrayscale(
        width: Int, height: Int, depth: Int,
        bytes: ByteArray, offset: Int, descriptor: Int, order: Order
    ): IntArray {
        var pixels: IntArray? = null
        val rs = order.redShift
        val gs = order.greenShift
        val bs = order.blueShift
        val `as` = order.alphaShift
        when(depth) {
            8 -> {
                pixels = IntArray(width * height)
                if(descriptor and RIGHT_ORIGIN != 0) {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val e = bytes[offset + width * i + j].toInt() and 0xFF
                                val a = 0xFF
                                pixels[width * i + (width - j - 1)] =
                                    e shl rs or (e shl gs) or (e shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val e = bytes[offset + width * i + j].toInt() and 0xFF
                                val a = 0xFF
                                pixels[width * (height - i - 1) + (width - j - 1)] =
                                    (e shl rs or (e shl gs) or (e shl bs)
                                            or (a shl `as`))
                                j++
                            }
                            i++
                        }
                    }
                } else {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val e = bytes[offset + width * i + j].toInt() and 0xFF
                                val a = 0xFF
                                pixels[width * i + j] = e shl rs or (e shl gs) or (e shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val e = bytes[offset + width * i + j].toInt() and 0xFF
                                val a = 0xFF
                                pixels[width * (height - i - 1) + j] =
                                    e shl rs or (e shl gs) or (e shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    }
                }
            }

            16 -> {
                pixels = IntArray(width * height)
                if(descriptor and RIGHT_ORIGIN != 0) {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val e = bytes[offset + 2 * width * i + 2 * j + 0].toInt() and 0xFF
                                val a = bytes[offset + 2 * width * i + 2 * j + 1].toInt() and 0xFF
                                pixels[width * i + (width - j - 1)] =
                                    e shl rs or (e shl gs) or (e shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerRight
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val e = bytes[offset + 2 * width * i + 2 * j + 0].toInt() and 0xFF
                                val a = bytes[offset + 2 * width * i + 2 * j + 1].toInt() and 0xFF
                                pixels[width * (height - i - 1) + (width - j - 1)] =
                                    (e shl rs or (e shl gs) or (e shl bs)
                                            or (a shl `as`))
                                j++
                            }
                            i++
                        }
                    }
                } else {
                    if(descriptor and UPPER_ORIGIN != 0) {
                        // UpperLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val e = bytes[offset + 2 * width * i + 2 * j + 0].toInt() and 0xFF
                                val a = bytes[offset + 2 * width * i + 2 * j + 1].toInt() and 0xFF
                                pixels[width * i + j] = e shl rs or (e shl gs) or (e shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    } else {
                        // LowerLeft
                        var i = 0
                        while(i < height) {
                            var j = 0
                            while(j < width) {
                                val e = bytes[offset + 2 * width * i + 2 * j + 0].toInt() and 0xFF
                                val a = bytes[offset + 2 * width * i + 2 * j + 1].toInt() and 0xFF
                                pixels[width * (height - i - 1) + j] =
                                    e shl rs or (e shl gs) or (e shl bs) or (a shl `as`)
                                j++
                            }
                            i++
                        }
                    }
                }
            }

            else -> throw IOException("Unsupported depth:$depth")
        }
        return pixels
    }

    class Order internal constructor(var redShift: Int, var greenShift: Int, var blueShift: Int, var alphaShift: Int)
}