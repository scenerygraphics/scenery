package graphics.scenery.volumes

import bvv.core.backend.GpuContext
import bvv.core.backend.Texture3D
import bvv.core.multires.SimpleStack3D
import bvv.core.render.*
import graphics.scenery.utils.lazyLogger
import net.imglib2.RandomAccessibleInterval
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.joml.Vector3f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.Consumer
import kotlin.collections.HashMap

open class SceneryStackManager: SimpleStackManager {
    private val logger by lazyLogger()
    private val texturesU8 : HashMap<Int, VolumeTextureU8> = HashMap()
    private val texturesU16 : HashMap<Int, VolumeTextureU16> = HashMap()
    private val texturesRGBA8 : HashMap<Int, VolumeTextureRGBA8> = HashMap()

    private val timestamps : HashMap<Texture3D, Int> = HashMap()
    private val uploaded = HashMap<Texture3D, Boolean>()

    private var currentTimestamp : Int = 0

    protected open fun getImageDimensions(stack: SimpleStack3D<*>): Pair<Vector3f, Vector3f> {
        return if(stack is BufferedSimpleStack3D) {
            val dim = stack.dimensions
            val sourceMin = Vector3f(0.0f, 0.0f, 0.0f)
            val sourceMax = Vector3f(dim[0].toFloat(), dim[1].toFloat(), dim[2].toFloat())

            sourceMin to sourceMax
        } else {
            val image = stack.image
            val sourceMax = Vector3f(image.max(0).toFloat(), image.max(1).toFloat(), image.max(2).toFloat())
            val sourceMin = Vector3f(image.min(0).toFloat(), image.min(1).toFloat(), image.min(2).toFloat())

            sourceMin to sourceMax
        }
    }

    private fun GpuContext.contextReadyForUpload(): Boolean {
        return if(this is SceneryContext) {
            this.node.readyToRender()
        } else {
            true
        }
    }

    @Synchronized
    override fun getSimpleVolume(context : GpuContext, stack : SimpleStack3D<*>) : SimpleVolume {
        val texture: Texture3D
        val (sourceMin, sourceMax) = getImageDimensions(stack)

//        logger.info("Existing textures: ${texturesU8.keys.joinToString { "$it (${it.hashCode().toString()})" }}")
//        logger.info("Getting volume for stack $stack/${stack.hashCode()}")
        if (stack.type is UnsignedByteType) {
            val existing = texturesU8[stack.hashCode()]
            if (existing == null) {
                texture = VolumeTextureU8()
                logger.info("U8 texture does not exist, creating new one ($texture)")
                if (stack is BufferedSimpleStack3D) {
                    texture.init(stack.dimensions)
                } else {
                    texture.init(Intervals.dimensionsAsIntArray(stack.image))
                }

                if(context.contextReadyForUpload()) {
                    logger.debug("Context is ready for upload")
                    upload(context, stack, texture)
                    uploaded[texture] = true
                } else {
                    logger.debug("Context not ready for upload, deferring.")
                    uploaded[texture] = false
                }

                texturesU8[stack.hashCode()] = texture
            } else {
                texture = existing

                if(stack is BufferedSimpleStack3D && stack.stale) {
                    uploaded.remove(texture)
                }

                if(context.contextReadyForUpload() && uploaded.getOrDefault(texture, false)) {
                    upload(context, stack, texture)
                    uploaded[texture] = true
                }

                if(stack is BufferedSimpleStack3D) {
                    stack.stale = uploaded.getOrDefault(texture, false)
                }
            }
        } else if (stack.type is UnsignedShortType) {
            val existing = texturesU16[stack.hashCode()]
            if (existing == null) {
                texture = VolumeTextureU16()
                logger.debug("U16 texture does not exist, creating new one ($texture)")
                if (stack is BufferedSimpleStack3D) {
                    texture.init(stack.dimensions)
                } else {
                    texture.init(Intervals.dimensionsAsIntArray(stack.image))
                }
                if(context.contextReadyForUpload()) {
                    upload(context, stack, texture)
                    uploaded[texture] = true
                } else {
                    uploaded[texture] = false
                }
                texturesU16[stack.hashCode()] = texture

            } else {
                texture = existing

                if(stack is BufferedSimpleStack3D && stack.stale) {
                    uploaded.remove(texture)
                }

                if(context.contextReadyForUpload() && uploaded.getOrDefault(texture, false)) {
                    upload(context, stack, texture)
                    uploaded[texture] = true
                }

                if(stack is BufferedSimpleStack3D) {
                    stack.stale = uploaded.getOrDefault(texture, false)
                    logger.debug("$stack.stale = ${stack.stale}")
                }
            }
        } else {
            throw IllegalArgumentException("Textures of type " + stack.getType() + " are not supported.")
        }

        timestamps[texture] = currentTimestamp
        logger.debug("Returning texture $texture from getSimpleStack")
        return SimpleVolume(texture, stack.sourceTransform, sourceMin, sourceMax)
    }

    private fun clearTextures() {
            texturesU8.clear()
            texturesU16.clear()
            texturesRGBA8.clear()
    }

    /**
     * Uplaods the given stack with the given GPU context.
     * @param context A GpuContext to use for uploading.
     * @param stack The stack to upload.
     * @return True, if data was uploaded. False, if not.
     */
    @Suppress("UNCHECKED_CAST", "USELESS_ELVIS")
    fun upload(context : GpuContext, stack : SimpleStack3D<*>, texture: Texture3D) : Boolean {
        if(context is SceneryContext && !context.contextReadyForUpload()) {
            return false
        }

        val type = stack.type
        val hasBeenUploaded = uploaded.getOrDefault(texture, false)

        if(hasBeenUploaded) {
            return false
        }

        when {
            type is UnsignedByteType && texture is VolumeTextureU8 -> {
                if (stack is BufferedSimpleStack3D) {
                    logger.debug("Uploading U8 buffered texture")
                    texture.init(stack.dimensions)
                    texture.upload(context, stack.buffer)
                } else {
                    logger.debug("Uploading U8 RAII texture")
                    uploadToTextureU8(context, stack.image as RandomAccessibleInterval<UnsignedByteType>, texture)
                }

                texturesU8[stack.hashCode()] = texture
            }

            type is UnsignedShortType && texture is VolumeTextureU16 -> {
                if (stack is BufferedSimpleStack3D) {
                    logger.debug("Uploading U16 buffered texture")
                    texture.init(stack.dimensions)
                    texture.upload(context, stack.buffer)
                } else {
                    logger.debug("Uploading U16 RAII texture")
                    uploadToTextureU16(context, stack.image as RandomAccessibleInterval<UnsignedShortType>, texture)
                }

                texturesU16[stack.hashCode()] = texture
            }

            type is ARGBType && texture is VolumeTextureRGBA8 -> {
                if (stack is BufferedSimpleStack3D) {
                    TODO("Not implemented upload for buffered ARGB textures yet")
                } else {
                    uploadToTextureRGBA8(context, stack.image as RandomAccessibleInterval<ARGBType>, texture)
                }

                texturesRGBA8[stack.hashCode()] = texture
            }
        }

        uploaded[texture] = true
        timestamps[texture] = currentTimestamp

        return true
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

    private fun uploadToTextureU16(context : GpuContext, rai : RandomAccessibleInterval<UnsignedShortType>, texture: VolumeTextureU16) : VolumeTextureU16 {
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

    private fun uploadToTextureU8(context : GpuContext, rai : RandomAccessibleInterval<UnsignedByteType>, texture: VolumeTextureU8) : VolumeTextureU8 {
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

    private fun uploadToTextureRGBA8(context : GpuContext, rai : RandomAccessibleInterval<ARGBType>, texture: VolumeTextureRGBA8) : VolumeTextureRGBA8 {
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

    fun clearReferences(t: Texture3D) {
        uploaded.remove(t)
        timestamps.remove(t)
    }
}
