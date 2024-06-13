package graphics.scenery.volumes

import graphics.scenery.utils.Image
import graphics.scenery.utils.lazyLogger
import net.imagej.lut.LUTService
import net.imglib2.display.ColorTable
import org.joml.Vector4f
import org.scijava.plugin.Parameter
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Class for holding RGBA colormaps for volumes
 */
class Colormap(val buffer: ByteBuffer, val width: Int, val height: Int) {

    // This needs to stay, Kryo needs it for (de)serialisation
    @Suppress("unused")
    private constructor() : this(ByteBuffer.allocate(0), 0, 0)

    /**
     * Returns the value of the color map sampled at the normalized position.
     *
     * position: A floating point value between 0 and 1.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    fun sample(position: Float): Vector4f {
        val bufferPosition: Float = position.coerceIn(0.0f, 1.0f) * (width - 1)
        val previous = bufferPosition.toInt()

        val band = height/2

        //The number of bytes per pixel are fixed at 4.
        val globalOffset = width * band * 4

        val b = buffer.duplicate()
        b.position(globalOffset + previous * 4);
        val color = ByteArray(4)
        b.get(color)
        //Add to "Image" utility class?
        val c1 = Vector4f(color[0].toUByte().toFloat() / 255f, color[1].toUByte().toFloat() / 255f, color[2].toUByte().toFloat() / 255f, color[3].toUByte().toFloat() / 255f)

        if (bufferPosition > previous){
            //interpolate fraction part.
            b.get(color);
            val c2 = Vector4f( color[0].toUByte().toFloat() / 255f, color[1].toUByte().toFloat() / 255f, color[2].toUByte().toFloat() / 255f, color[3].toUByte().toFloat() / 255f)
            return c1.lerp(c2, bufferPosition - previous.toFloat())
        } else {
            return c1
        }

    }

    companion object {
        val logger by lazyLogger()

        @Parameter
        var lutService: LUTService? = null

        /**
         * Creates a new color map from a [ByteBuffer], with dimensions given as [width] and [height].
         */
        @JvmStatic fun fromBuffer(buffer: ByteBuffer, width: Int, height: Int): Colormap {
            return Colormap(buffer.duplicate(), width, height)
        }

        /**
         * Creates a new colormap from an [array], with dimensions given as [width] and [height].
         */
        @JvmStatic fun fromArray(array: ByteArray, width: Int, height: Int): Colormap {
            return Colormap(ByteBuffer.wrap(array), width, height)
        }

        /**
         * Creates a new colormap from an [stream], with the file type/extension given in [extension].
         */
        @JvmStatic fun fromStream(stream: InputStream, extension: String): Colormap {
            val image = Image.fromStream(stream, extension)
            logger.debug("Read image from $stream with ${image.contents.remaining()} bytes, size=${image.width}x${image.height}")
            return Colormap(image.contents, image.width, image.height)
        }

        /**
         * Creates a color map from an imglib2 [ColorTable].
         */
        @JvmStatic fun fromColorTable(colorTable: ColorTable): Colormap {
            val copies = 16
            val byteBuffer = ByteBuffer.allocateDirect(
                4 * colorTable.length * copies) // Num bytes * num components * color map length * height of color map texture
            val tmp = ByteArray(4 * colorTable.length)
            for (k in 0 until colorTable.length) {
                for (c in 0 until colorTable.componentCount) { // TODO this assumes numBits is 8, could be 16
                    tmp[4 * k + c] = colorTable[c, k].toByte()
                }
                if (colorTable.componentCount == 3) {
                    tmp[4 * k + 3] = 255.toByte()
                }
            }
            for (i in 0 until copies) {
                byteBuffer.put(tmp)
            }
            byteBuffer.flip()

            logger.debug("Using ImageJ colormap $colorTable with size ${colorTable.length}x$copies")
            return fromBuffer(byteBuffer, colorTable.length, copies)
        }

        /**
         * Creates a color map from an imglib2 [ColorTable].
         */
        @JvmStatic fun fromColor(color: Vector4f): Colormap {
            val copies = 16
            val width = 256
            val byteBuffer = ByteBuffer.allocateDirect(
                4 * width * copies) // Num bytes * num components * color map length * height of color map texture
            val tmp = ByteArray(4 * width)
            for (i in 0 until width) {
                tmp[4 * i + 0] = (256 - color[0] * i).toInt().toByte()
                tmp[4 * i + 1] = (256 - color[1] * i).toInt().toByte()
                tmp[4 * i + 2] = (256 - color[2] * i).toInt().toByte()
                tmp[4 * i + 3] = 255.toByte()
            }
            for (i in 0 until copies) {
                byteBuffer.put(tmp)
            }
            byteBuffer.flip()

            return fromBuffer(byteBuffer, width, copies)
        }

        /**
         * Creates a color map from a png file.
         */
        fun fromPNGFile(file: File): Colormap {
            var img: BufferedImage? = null
            try {
                img = ImageIO.read(file)
            } catch (_: IllegalArgumentException){
                logger.error("Could not find file ${file.path}")
            } catch (e: IOException){
                logger.error(e.toString())
            }
            if (img == null) throw IllegalArgumentException("Could not open png file $file")
            return fromBuffer(Image.bufferedImageToRGBABuffer(img),img.width, img.height)
        }

        /**
         * Tries to load a colormap from a file. Available colormaps can be queried with [list].
         */
        @JvmStatic fun get(name: String): Colormap {
            try {
                val luts = lutService?.findLUTs()
                val colorTable = luts?.let {
                    val url = it[name] ?: throw IOException("Color map $name not found in ImageJ colormaps")
                    lutService?.loadLUT(url)
                } ?: throw IOException("Color map $name not found in ImageJ colormaps")

                return fromColorTable(colorTable)
            } catch (e: IOException) {
                logger.debug("LUT $name not found as ImageJ colormap, trying stream")
                logger.debug("Using colormap $name from stream")
                val resource = Colormap::class.java.getResourceAsStream("colormap-$name.png")
                    ?: throw FileNotFoundException("Could not find color map for name $name (colormap-$name.png)")

                return fromStream(resource, "png")
            }
        }

        /**
         * Returns a list of strings containing the names of the available color maps for use with [get].
         */
        @JvmStatic fun list(): List<String> {
            // FIXME: Hardcoded for the moment, not nice.
            val list = arrayListOf("grays", "hot", "jet", "plasma", "viridis", "red-blue", "rb-darker")
            lutService?.findLUTs()?.keys?.forEach { list.add(it) }

            return list
        }
    }
}
