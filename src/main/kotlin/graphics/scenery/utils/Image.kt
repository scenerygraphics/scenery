package graphics.scenery.utils

import io.scif.SCIFIO
import net.imglib2.img.Img
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.real.FloatType
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyexr.EXRHeader
import org.lwjgl.util.tinyexr.EXRImage
import org.lwjgl.util.tinyexr.EXRVersion
import org.lwjgl.util.tinyexr.TinyEXR
import org.lwjgl.util.tinyexr.TinyEXR.FreeEXRImage
import org.scijava.Context
import org.scijava.io.handle.DataHandleService
import org.scijava.io.location.BytesLocation
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
import kotlin.system.measureNanoTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Utility class for reading RGBA images via [BufferedImage].
 * Image can store the image's RGBA content in [contents], and also stores image [width] and [height].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
open class Image(val contents: ByteBuffer, val width: Int, val height: Int, val depth: Int = 1, var type: NumericType<*> = UnsignedByteType()) {

    companion object {
        protected val logger by lazyLogger()
        protected val scifio = SCIFIO()

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

        // Used by TinyEXR image loader
        private fun check(result: Int, err: PointerBuffer) {
            if (result < 0) {
                val msg = err.getStringASCII(0)
                TinyEXR.nFreeEXRErrorMessage(err[0])
                throw IllegalStateException("EXR error: $result | $msg")
            }
        }

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
            var width: Int
            var height: Int

            when {
                extension.lowercase().endsWith("exr") -> {
                    val memory: ByteBuffer

                    try {
                        val array = stream.readAllBytes()
                        memory = MemoryUtil.memAlloc(array.size)
                        memory.put(array)
                        memory.flip()
                    } catch (e: IOException) {
                        logger.error("Failed to read EXR file ${e}.")
                        if(logger.isDebugEnabled) {
                            e.printStackTrace()
                        }
                        throw e
                    }

                    var result: Int
                    val image: EXRImage
                    val header: EXRHeader

                    val stack = MemoryStack.stackPush()
                    header = EXRHeader.malloc(stack)
                    val version = EXRVersion.malloc(stack)
                    val err: PointerBuffer = stack.mallocPointer(1)
                    result = TinyEXR.ParseEXRVersionFromMemory(version, memory)
                    check(result >= 0) { "Failed to parse EXR image version: $result" }
                    logger.debug("Parsed EXR image version successfully.")
                    logger.debug("--------------------------------------")
                    logger.debug("Version : " + version.version())
                    logger.debug("Tiled : " + version.tiled())
                    logger.debug("Long name : " + version.long_name())
                    logger.debug("Non-image : " + version.non_image())
                    logger.debug("Multipart : " + version.multipart())
                    TinyEXR.InitEXRHeader(header)
                    result = TinyEXR.ParseEXRHeaderFromMemory(header, version, memory, err)
                    check(result, err)
                    logger.debug("Parsed EXR image header successfully.")
                    logger.debug("-------------------------------------")
                    logger.debug("Pixel aspect ratio : " + header.pixel_aspect_ratio())
                    logger.debug("Line order : " + header.line_order())
                    logger.debug("Data window : " + header.data_window().min_x() + ", " + header.data_window().min_y() + " -> " + header.data_window().max_x() + ", " + header.data_window().max_y())
                    logger.debug("Display window : " + header.display_window().min_x() + ", " + header.display_window().min_y() + " -> " + header.display_window().max_x() + ", " + header.display_window().max_y())
                    logger.debug("Screen window center : " + header.screen_window_center()[0] + ", " + header.screen_window_center()[1])
                    logger.debug("Screen window width : " + header.screen_window_width())
                    logger.debug("Chunk count : " + header.chunk_count())
                    logger.debug("Tiled : " + header.tiled())
                    logger.debug("Tile size x : " + header.tile_size_x())
                    logger.debug("Tile size y : " + header.tile_size_y())
                    logger.debug("Tile level mode : " + header.tile_level_mode())
                    logger.debug("Tile rounding mode : " + header.tile_rounding_mode())
                    logger.debug("Long name : " + header.long_name())
                    logger.debug("Non-image : " + header.non_image())
                    logger.debug("Multipart : " + header.multipart())
                    logger.debug("Header length : " + header.header_len())
                    logger.debug("Number of custom attributes : " + header.num_custom_attributes())
                    logger.debug("Custom attributes : " + header.custom_attributes())
                    logger.debug("Channels : " + header.channels().remaining())
                    logger.debug("Pixel types : " + header.pixel_types().remaining())
                    logger.debug("Number of channels : " + header.num_channels())
                    logger.debug("Compression type : " + header.compression_type())
                    logger.debug("Requested pixel types : " + header.requested_pixel_types().remaining())
                    logger.debug("Name : " + header.nameString())

                    image = EXRImage.malloc(stack)
                    TinyEXR.InitEXRImage(image)
                    result = TinyEXR.LoadEXRImageFromMemory(image, header, memory, err)
                    check(result, err)
                    logger.info("Parsed EXR image successfully.")
                    logger.debug("------------------------------")
                    logger.debug("Level x: " + image.level_x())
                    logger.debug("Level y: " + image.level_y())
                    logger.debug("Images: " + image.images()!!.remaining())
                    logger.debug("Width: " + image.width())
                    logger.debug("Height: " + image.height())
                    logger.debug("Number of channels: " + image.num_channels())
                    logger.debug("Number of tiles: " + image.num_tiles())

                    val pb = image.images()
                        ?: throw UnsupportedOperationException("No images found. Image is likely tiled image which is currently not supported.")
                    val size = image.width()*image.height()
                    val bb = MemoryUtil.memAlloc(size*4*4)
                    val tmp = ByteBuffer.allocate(4)

                    for(i in size-1 downTo 0) {
                        for(c in 0..2) {
                            val b = MemoryUtil.memByteBuffer(pb[c]+i*4, 4)
                            tmp.put(0, b.get(3))
                            tmp.put(1, b.get(2))
                            tmp.put(2, b.get(1))
                            tmp.put(3, b.get(0))
                            bb.putFloat(tmp.asFloatBuffer().get(0))
                        }
                        bb.putFloat(1.0f)
                    }

                    bb.flip()

                    FreeEXRImage(image)
                    TinyEXR.FreeEXRHeader(header)

                    stream.close()

                    return Image(bb, image.width(), image.height(), type = FloatType())
                }

                else -> {
                    if(extension.lowercase().endsWith("tga")) {
                        imageData = try {
                            val reader = BufferedInputStream(stream)
                            buffer = ByteArray(stream.available())
                            reader.read(buffer)
                            reader.close()

                            pixels = TGAReader.read(buffer, TGAReader.RGBA)
                            width = TGAReader.getWidth(buffer)
                            height = TGAReader.getHeight(buffer)
                            val b = ByteBuffer.allocateDirect(width * height * 4)
                            b.asIntBuffer().put(pixels)
                            b
                        } catch (e: IOException) {
                            logger.error("Could not read image from TGA. ${e.message}")
                            width = 1
                            height = 1
                            ByteBuffer.wrap(byteArrayOf(127,127,127,0))
                        }

                    } else {
                        imageData = try {
                            // TODO: Improve this code here
                            val ctx = Context()//PluginService::class.java, SCIFIOService::class.java, DataHandleService::class.java)
                            val ds = ctx.getService(DataHandleService::class.java)
                            val h = ds.create(BytesLocation(stream.readAllBytes()))
                            val opener = scifio.io().getOpener(h.get())
                            logger.info("Opener: $opener")
                            val img = opener.open(h.get()) as? Img<UnsignedByteType>
                            logger.info("opened $img!")
                            val reader = scifio.initializer().initializeReader(h.get())
                            logger.info("Got reader")
                            val plane = reader.openPlane(0, 0)
                            logger.info("Opened plane with ${plane.bytes.size}b!")
                            val b = ByteBuffer.allocateDirect(plane.bytes.size)
                            b.put(plane.bytes)
                            b.flip()
                            width = plane.lengths[0].toInt()
                            height = plane.lengths[1].toInt()
                            reader.close()
                            b
                        } catch (e: IOException) {
                            logger.error("Could not read image: ${e.message}")
                            width = 1
                            height = 1
                            ByteBuffer.wrap(byteArrayOf(127,127,127,0))
                        }
                    }

                    stream.close()

                    if(flip) {
                        // convert to OpenGL UV space
                        flipInPlace(imageData, width, height)
                    }

                    return Image(imageData, width, height)
                }
            }
        }

        private fun flipInPlace(imageData: ByteBuffer, width: Int, height: Int) {
            val view = imageData.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            (0..height / 2).forEach { index ->
                val sourceIndex = index * width * 4
                val destIndex = width * height * 4 - (index + 1) * width * 4

                val source = ByteArray(width * 4)
                view.position(sourceIndex)
                view.get(source)

                val dest = ByteArray(width * 4)
                view.position(destIndex)
                view.get(dest)

                view.position(sourceIndex)
                view.put(dest)

                view.position(destIndex)
                view.put(source)
            }
        }

        /**
         * Creates an Image from a resource given in [path], with [baseClass] as basis for the search path.
         * [path] is expected to end in an extension (e.g., ".png"), such that the file type can be determined.
         */
        @JvmStatic fun fromResource(path: String, baseClass: Class<*>): Image {
            return fromStream(baseClass.getResourceAsStream(path), path.substringAfterLast(".").lowercase())
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

            imageBuffer = MemoryUtil.memAlloc(data.size)
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
