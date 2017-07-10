package graphics.scenery.utils

import java.lang.reflect.Array
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer

import coremem.ContiguousMemoryInterface
import javafx.scene.image.WritableImage
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Direct writable image

 * @author Loic Royer <royerloic@gmail.com>, Ulrik Guenther <hello@ulrik.is>
 */
class DirectWritableImage(pWidth: Int, pHeight: Int) : WritableImage(pWidth, pHeight) {
    private var getWritablePlatformImage: Method
    private var pixelBuffer: Field
    private var serial: Field
    private var pixelsDirty: Method

    init {
        getWritablePlatformImage = javafx.scene.image.Image::class.java.getDeclaredMethod("getWritablePlatformImage")
        getWritablePlatformImage.isAccessible = true

        pixelBuffer = com.sun.prism.Image::class.java.getDeclaredField("pixelBuffer")
        pixelBuffer.isAccessible = true

        pixelsDirty = javafx.scene.image.Image::class.java.getDeclaredMethod("pixelsDirty")
        pixelsDirty.isAccessible = true

        serial = com.sun.prism.Image::class.java.getDeclaredField("serial")
        serial.isAccessible = true
    }

    /**
     * Replaces the internal image buffer with the given one.

     * @param pMemory
     * *          new buffer
     */
    fun replaceBuffer(pMemory: ContiguousMemoryInterface) {
        try {
            replaceImageBuffer(pMemory.byteBuffer, this)
        } catch (e: Throwable) {
            throw RuntimeException("Error while replacing internal buffer",
                e)
        }

    }

    /**
     * Writes the contents of this memory object into the image

     * @param pMemory
     * *          memory
     */
    fun writePixels(pMemory: ContiguousMemoryInterface) {
        try {
            writeToImageDirectly(pMemory.byteBuffer, this)
        } catch (e: Throwable) {
            throw RuntimeException("Error while writting pixels", e)
        }

    }

    @Throws(NoSuchMethodException::class, IllegalAccessException::class, InvocationTargetException::class, NoSuchFieldException::class)
    private fun writeToImageDirectly(direct: ByteBuffer,
                                     writableImg: WritableImage) {
        // Get the platform image
        val prismImg = getWritablePlatformImage.invoke(writableImg) as com.sun.prism.Image

        // Replace the buffer
        pixelBuffer.set(prismImg, direct)

        forceUpdate(writableImg, prismImg)
    }

    fun update(buffer: ByteBuffer) {
        writeToImageDirectly(buffer, this)
    }

    @Throws(NoSuchMethodException::class, IllegalAccessException::class, InvocationTargetException::class, NoSuchFieldException::class)
    private fun replaceImageBuffer(direct: ByteBuffer,
                                   writableImg: WritableImage) {
        // Get the platform image
        val getWritablePlatformImage = javafx.scene.image.Image::class.java.getDeclaredMethod("getWritablePlatformImage")
        getWritablePlatformImage.isAccessible = true
        val prismImg = getWritablePlatformImage.invoke(writableImg) as com.sun.prism.Image

        // Replace the buffer
        val pixelBuffer = com.sun.prism.Image::class.java.getDeclaredField("pixelBuffer")
        pixelBuffer.isAccessible = true
        pixelBuffer.set(prismImg, direct)

        forceUpdate(writableImg, prismImg)
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class, NoSuchMethodException::class, InvocationTargetException::class)
    private fun forceUpdate(writableImg: WritableImage,
                            prismImg: com.sun.prism.Image) {
        // Invalidate the platform image
        Array.setInt(serial.get(prismImg),
            0,
            Array.getInt(serial.get(prismImg), 0) + 1)

        // Invalidate the WritableImage
        pixelsDirty.invoke(writableImg)
    }

}
