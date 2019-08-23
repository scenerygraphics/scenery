package graphics.scenery.volumes.bdv

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.HashMap
import net.imglib2.RandomAccessibleInterval
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.joml.Vector3f
import tpietzsch.backend.GpuContext
import tpietzsch.backend.Texture3D
import tpietzsch.example2.SimpleStackManager
import tpietzsch.example2.SimpleVolume
import tpietzsch.example2.VolumeTextureRGBA8
import tpietzsch.example2.VolumeTextureU16
import tpietzsch.example2.VolumeTextureU8
import tpietzsch.multires.SimpleStack3D
import java.util.function.Consumer

open class SceneryStackManager: SimpleStackManager {
    private val texturesU8 : HashMap<SimpleStack3D<*>, VolumeTextureU8>
    private val texturesU16 : HashMap<SimpleStack3D<*>, VolumeTextureU16>
    private val texturesRGBA8 : HashMap<SimpleStack3D<*>, VolumeTextureRGBA8>

    private val timestamps : HashMap<Texture3D, Int>

    private var currentTimestamp : Int = 0

    init {
        texturesU16 = HashMap()
        texturesU8 = HashMap()
        texturesRGBA8 = HashMap()
        timestamps = HashMap()
        currentTimestamp = 0
    }

    @Synchronized
    override fun getSimpleVolume(context : GpuContext, stack : SimpleStack3D<*>) : SimpleVolume {
        val image = stack.image
        val type = stack.type

        val texture : Texture3D
        val sourceMax : Vector3f
        val sourceMin : Vector3f

        if (stack is BufferedSimpleStack3D) {
            val dim = stack.dimensions
            sourceMin = Vector3f(0.0f, 0.0f, 0.0f)
            sourceMax = Vector3f(dim[0].toFloat(), dim[1].toFloat(), dim[2].toFloat())

            if (stack.getType() is UnsignedByteType) {
                val existing = texturesU8[stack]
                if (existing == null) {
                    texture = VolumeTextureU8()
                    texture.init(stack.dimensions)
                    texture.upload(context, stack.buffer)

                    texturesU8[stack] = texture
                } else {
                    texture = existing
                }
            } else if (stack.getType() is UnsignedShortType) {
                val existing = texturesU16[stack]
                if (existing == null) {
                    texture = VolumeTextureU16()
                    texture.init(stack.dimensions)
                    texture.upload(context, stack.buffer)

                    texturesU16[stack] = texture
                } else {
                    texture = existing
                }
            } else {
                throw IllegalArgumentException("Textures of type " + stack.getType() + " are not supported.")
            }

            return SimpleVolume(texture, stack.getSourceTransform(), sourceMin, sourceMax)
        }

        if (type is UnsignedShortType) {
            texture = (texturesU16 as MutableMap<SimpleStack3D<*>, VolumeTextureU16>).computeIfAbsent(stack) { s -> uploadToTextureU16(context, image as RandomAccessibleInterval<UnsignedShortType>) }
            sourceMax = Vector3f(image.max(0).toFloat(), image.max(1).toFloat(), image.max(2).toFloat())
            sourceMin = Vector3f(image.min(0).toFloat(), image.min(1).toFloat(), image.min(2).toFloat())
        } else if (type is UnsignedByteType) {
            texture = (texturesU8 as MutableMap<SimpleStack3D<*>, VolumeTextureU8>).computeIfAbsent(stack) { s -> uploadToTextureU8(context, image as RandomAccessibleInterval<UnsignedByteType>) }
            sourceMax = Vector3f(image.max(0).toFloat(), image.max(1).toFloat(), image.max(2).toFloat())
            sourceMin = Vector3f(image.min(0).toFloat(), image.min(1).toFloat(), image.min(2).toFloat())
        } else if (type is ARGBType) {
            texture = (texturesRGBA8 as MutableMap<SimpleStack3D<*>, VolumeTextureRGBA8>).computeIfAbsent(stack) { s -> uploadToTextureRGBA8(context, image as RandomAccessibleInterval<ARGBType>) }
            sourceMax = Vector3f(image.max(0).toFloat(), image.max(1).toFloat(), image.max(2).toFloat())
            sourceMin = Vector3f(image.min(0).toFloat(), image.min(1).toFloat(), image.min(2).toFloat())
        } else
            throw IllegalArgumentException()

        timestamps[texture] = currentTimestamp

        return SimpleVolume(texture, stack.sourceTransform, sourceMin, sourceMax)
    }

    /**
     * Uplaods the given stack with the given GPU context.
     * @param context A GpuContext to use for uploading.
     * @param stack The stack to upload.
     * @return True, if data was uploaded. False, if not.
     */
    fun upload(context : GpuContext, stack : SimpleStack3D<*>) : Boolean {
        val tex8 = texturesU8[stack]
        val tex16 = texturesU16[stack]
        val texARGB = texturesRGBA8[stack]

        if (tex8 != null) {
            if (stack is BufferedSimpleStack3D) {
                tex8.upload(context, stack.buffer)
                return true
            } else {
                uploadToTextureU8(context, stack.image as RandomAccessibleInterval<UnsignedByteType>)
                return true
            }
        }

        if (tex16 != null) {
            if (stack is BufferedSimpleStack3D) {
                tex16.upload(context, stack.buffer)
                return true
            } else {
                uploadToTextureU16(context, stack.image as RandomAccessibleInterval<UnsignedShortType>)
                return true
            }
        }

        if (texARGB != null) {
            if (stack is BufferedSimpleStack3D) {
                texARGB.upload(context, stack.buffer)
                return true
            } else {
                uploadToTextureRGBA8(context, stack.image as RandomAccessibleInterval<ARGBType>)
                return true
            }
        }

        return false
    }

    /**
     * Free allocated resources associated to all stacks that have not been
     * [requested][.getSimpleVolume] since the
     * last call to [.freeUnusedSimpleVolumes].
     */
    @Synchronized
    override fun freeUnusedSimpleVolumes(context : GpuContext) {
        val it = timestamps.entries.iterator()

        texturesU16.entries.removeIf { entry -> timestamps[entry.value]!! < currentTimestamp }
        texturesU8.entries.removeIf { entry -> timestamps[entry.value]!! < currentTimestamp }
        texturesRGBA8.entries.removeIf { entry -> timestamps[entry.value]!! < currentTimestamp }
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.value < currentTimestamp) {
                context.delete(entry.key)
                it.remove()
            }
        }
        ++currentTimestamp
    }

    override fun freeSimpleVolumes(context : GpuContext) {
        texturesU16.clear()
        texturesU8.clear()
        texturesRGBA8.clear()
        timestamps.keys.forEach(Consumer<Texture3D> { context.delete(it) })
        timestamps.clear()
    }

    private fun uploadToTextureU16(context : GpuContext, rai : RandomAccessibleInterval<UnsignedShortType>) : VolumeTextureU16 {
        val texture = VolumeTextureU16()
        texture.init(Intervals.dimensionsAsIntArray(rai))

        val numBytes = (2 * Intervals.numElements(rai)).toInt()
        val data = ByteBuffer.allocateDirect(numBytes) // allocate a bit more than needed...
        data.order(ByteOrder.nativeOrder())
        copyToBufferU16(rai, data)
        texture.upload(context, data)
        return texture
    }

    private fun copyToBufferU16(rai : RandomAccessibleInterval<UnsignedShortType>, buffer : ByteBuffer) {
        // TODO handle specific RAI types more efficiently
        // TODO multithreading
        val cursor = Views.flatIterable(rai).cursor()
        val sdata = buffer.asShortBuffer()
        var i = 0
        while (cursor.hasNext())
            sdata.put(i++, cursor.next().short)
    }

    private fun uploadToTextureU8(context : GpuContext, rai : RandomAccessibleInterval<UnsignedByteType>) : VolumeTextureU8 {
        val texture = VolumeTextureU8()
        texture.init(Intervals.dimensionsAsIntArray(rai))

        val numBytes = Intervals.numElements(rai).toInt()
        val data = ByteBuffer.allocateDirect(numBytes) // allocate a bit more than needed...
        data.order(ByteOrder.nativeOrder())
        copyToBufferU8(rai, data)
        texture.upload(context, data)
        return texture
    }

    private fun copyToBufferU8(rai : RandomAccessibleInterval<UnsignedByteType>, buffer : ByteBuffer) {
        // TODO handle specific RAI types more efficiently
        // TODO multithreading
        val cursor = Views.flatIterable(rai).cursor()
        var i = 0
        while (cursor.hasNext())
            buffer.put(i++, cursor.next().byte)
    }

    private fun uploadToTextureRGBA8(context : GpuContext, rai : RandomAccessibleInterval<ARGBType>) : VolumeTextureRGBA8 {
        val texture = VolumeTextureRGBA8()
        texture.init(Intervals.dimensionsAsIntArray(rai))

        val numBytes = (4 * Intervals.numElements(rai)).toInt()
        val data = ByteBuffer.allocateDirect(numBytes) // allocate a bit more than needed...
        data.order(ByteOrder.nativeOrder())
        copyToBufferRGBA8(rai, data)
        texture.upload(context, data)
        return texture
    }

    private fun copyToBufferRGBA8(rai : RandomAccessibleInterval<ARGBType>, buffer : ByteBuffer) {
        // TODO handle specific RAI types more efficiently
        // TODO multithreading
        val cursor = Views.flatIterable(rai).cursor()
        val sdata = buffer.asIntBuffer()
        var i = 0
        while (cursor.hasNext())
            sdata.put(i++, toRGBA(cursor.next().get()))
    }

    private fun toRGBA(argb : Int) : Int {
        val a = argb shr 24 and 0xff
        val r = argb shr 16 and 0xff
        val g = argb shr 8 and 0xff
        val b = argb and 0xff
        return a shl 24 or (b shl 16) or (g shl 8) or r
    }
}
