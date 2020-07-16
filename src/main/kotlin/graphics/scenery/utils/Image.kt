package graphics.scenery.utils

import cleargl.TGAReader
import graphics.scenery.volumes.Colormap
import java.awt.Color
import java.awt.color.ColorSpace
import java.awt.geom.AffineTransform
import java.awt.image.*
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.imageio.ImageIO

/**
 * Utility class for reading RGBA images via [BufferedImage].
 * Image can store the image's RGBA content in [contents], and also stores image [width] and [height].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
open class Image(val contents: ByteBuffer, val width: Int, val height: Int, val depth: Int = 1) {

    companion object {
        protected val logger by LazyLogger()

        private val StandardAlphaColorModel = ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            intArrayOf(8, 8, 8, 8),
            true,
            false,
            ComponentColorModel.TRANSLUCENT,
            DataBuffer.TYPE_BYTE)

        private val StandardColorModel = ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            intArrayOf(8, 8, 8, 0),
            false,
            false,
            ComponentColorModel.OPAQUE,
            DataBuffer.TYPE_BYTE)

        /**
         * Creates a new [Image] from a [stream], with the [extension] given. The image
         * can be [flip]ped to accomodate Vulkan and OpenGL coordinate systems.
         */
        @JvmStatic @JvmOverloads fun fromStream(stream: InputStream, extension: String, flip: Boolean = true): Image  {

            var bi: BufferedImage
            val flippedImage: BufferedImage
            val imageData: ByteBuffer
            val pixels: IntArray
            val buffer: ByteArray

            if (extension.toLowerCase().endsWith("tga")) {
                try {
                    val reader = BufferedInputStream(stream)
                    buffer = ByteArray(stream.available())
                    reader.read(buffer)
                    reader.close()

                    pixels = TGAReader.read(buffer, TGAReader.ARGB)
                    val width = TGAReader.getWidth(buffer)
                    val height = TGAReader.getHeight(buffer)
                    bi = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    bi.setRGB(0, 0, width, height, pixels, 0, width)
                } catch (e: IOException) {
                    Colormap.logger.error("Could not read image from TGA. ${e.message}")
                    bi = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                    bi.setRGB(0, 0, 1, 1, intArrayOf(255, 0, 0), 0, 1)
                }

            } else {
                try {
                    val reader = BufferedInputStream(stream)
                    bi = ImageIO.read(stream)
                    reader.close()

                } catch (e: IOException) {
                    Colormap.logger.error("Could not read image: ${e.message}")
                    bi = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                    bi.setRGB(0, 0, 1, 1, intArrayOf(255, 0, 0), 0, 1)
                }

            }

            stream.close()

            if(flip) {
                // convert to OpenGL UV space
                flippedImage = createFlipped(bi)
                imageData = bufferedImageToRGBABuffer(flippedImage)
            } else {
                imageData = bufferedImageToRGBABuffer(bi)
            }

            return Image(imageData, bi.width, bi.height)
        }

        /**
         * Creates an Image from a resource given in [path], with [baseClass] as basis for the search path.
         * [path] is expected to end in an extension (e.g., ".png"), such that the file type can be determined.
         */
        @JvmStatic fun fromResource(path: String, baseClass: Class<*>): Image {
            return fromStream(baseClass.getResourceAsStream(path), path.substringAfterLast(".").toLowerCase())
        }

        /**
         * Converts a buffered image to an RGBA byte buffer.
         */
        fun bufferedImageToRGBABuffer(bufferedImage: BufferedImage): ByteBuffer {
            val imageBuffer: ByteBuffer
            val raster: WritableRaster
            val texImage: BufferedImage

            var texWidth = bufferedImage.width
            var texHeight = bufferedImage.height

//            while (texWidth < bufferedImage.width) {
//                texWidth *= 2
//            }
//            while (texHeight < bufferedImage.height) {
//                texHeight *= 2
//            }

            if (bufferedImage.colorModel.hasAlpha()) {
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, texWidth, texHeight, 4, null)
                texImage = BufferedImage(StandardAlphaColorModel, raster, false, Hashtable<Any, Any>())
            } else {
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, texWidth, texHeight, 3, null)
                texImage = BufferedImage(StandardColorModel, raster, false, Hashtable<Any, Any>())
            }

            val g = texImage.graphics
            g.color = Color(0.0f, 0.0f, 0.0f, 1.0f)
            g.fillRect(0, 0, texWidth, texHeight)
            g.drawImage(bufferedImage, 0, 0, null)
            g.dispose()

            val data = (texImage.raster.dataBuffer as DataBufferByte).data

            imageBuffer = ByteBuffer.allocateDirect(data.size)
            imageBuffer.order(ByteOrder.nativeOrder())
            imageBuffer.put(data, 0, data.size)
            imageBuffer.rewind()

            return imageBuffer
        }

        /**
         * Flips a given [BufferedImage] and returns it.
         */
        // the following three routines are from
        // http://stackoverflow.com/a/23458883/2129040,
        // authored by MarcoG
        fun createFlipped(image: BufferedImage): BufferedImage {
            val at = AffineTransform()
            at.concatenate(AffineTransform.getScaleInstance(1.0, -1.0))
            at.concatenate(AffineTransform.getTranslateInstance(0.0, (-image.height).toDouble()))
            return createTransformed(image, at)
        }

        /**
         * Transforms a given [image] by [AffineTransform] [at].
         */
        private fun createTransformed(
            image: BufferedImage, at: AffineTransform): BufferedImage {
            val newImage = BufferedImage(
                image.width, image.height,
                BufferedImage.TYPE_INT_ARGB)
            val g = newImage.createGraphics()
            g.transform(at)
            g.drawImage(image, 0, 0, null)
            g.dispose()
            return newImage
        }
    }
}
