package graphics.scenery.volumes

import graphics.scenery.utils.LazyLogger
import net.imglib2.RandomAccessibleInterval
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.joml.Vector3f
import tpietzsch.backend.GpuContext
import tpietzsch.backend.Texture3D
import tpietzsch.example2.*
import tpietzsch.multires.SimpleStack3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.Consumer
import kotlin.collections.HashMap

open class SceneryStackManager: SimpleStackManager {
    private val logger by LazyLogger()
    private val texturesU8 : HashMap<SimpleStack3D<*>, VolumeTextureU8> = HashMap()
    private val texturesU16 : HashMap<SimpleStack3D<*>, VolumeTextureU16> = HashMap()
    private val texturesRGBA8 : HashMap<SimpleStack3D<*>, VolumeTextureRGBA8> = HashMap()

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
            val existing = texturesU8[stack]
            if (existing == null) {
                texture = VolumeTextureU8()
                logger.info("U8 texture does not exist, creating new one ($texture)")
                if (stack is BufferedSimpleStack3D) {
                    texture.init(stack.dimensions)
                } else {
                    texture.init(Intervals.dimensionsAsIntArray(stack.image))
                }
                if(context.contextReadyForUpload()) {
                    upload(context, stack)
                    uploaded.put(texture, true)
                } else {
                    uploaded.put(texture, false)
                }
                texturesU8[stack] = texture
            } else {
                texture = existing

                if(stack is BufferedSimpleStack3D && stack.stale) {
                    uploaded.remove(texture)
                }

                if(context.contextReadyForUpload() && uploaded.getOrDefault(texture, false)) {
                    upload(context, stack)
                    uploaded.put(texture, true)
                }

                if(stack is BufferedSimpleStack3D) {
                    stack.stale = uploaded.getOrDefault(texture, false)
                }
            }
        } else if (stack.type is UnsignedShortType) {
            val existing = texturesU16[stack]
            if (existing == null) {
                texture = VolumeTextureU16()
                logger.info("U16 texture does not exist, creating new one ($texture)")
                if (stack is BufferedSimpleStack3D) {
                    texture.init(stack.dimensions)
                } else {
                    texture.init(Intervals.dimensionsAsIntArray(stack.image))
                }
                if(context.contextReadyForUpload()) {
                    upload(context, stack)
                    uploaded.put(texture, true)
                } else {
                    uploaded.put(texture, false)
                }
                texturesU16[stack] = texture

            } else {
                texture = existing

                if(stack is BufferedSimpleStack3D && stack.stale) {
                    uploaded.remove(texture)
                }

                if(context.contextReadyForUpload() && uploaded.getOrDefault(texture, false)) {
                    upload(context, stack)
                    uploaded[texture] = true
                }

                if(stack is BufferedSimpleStack3D) {
                    stack.stale = uploaded.getOrDefault(texture, false)
                    logger.info("$stack.stale = ${stack.stale}")
                }
            }
        } else {
            throw IllegalArgumentException("Textures of type " + stack.getType() + " are not supported.")
        }

        timestamps[texture] = currentTimestamp
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
    fun upload(context : GpuContext, stack : SimpleStack3D<*>) : Boolean {
        if(context is SceneryContext && !context.contextReadyForUpload()) {
            return false
        }

        val tex8 = texturesU8[stack]
        val tex16 = texturesU16[stack]
        val texARGB = texturesRGBA8[stack]

        val type = stack.type
//        logger.info("$stack, $type -> $tex8, $tex16, $texARGB, ${uploaded.getOrDefault(tex8 as? Texture3D, false)}")

        if (type is UnsignedByteType && !(tex8 == null || uploaded.getOrDefault(tex8, false))) {
            return if (stack is BufferedSimpleStack3D) {
                logger.info("Uploading U8 buffered texture")
                val tex = tex8 ?: VolumeTextureU8()
                tex.init(stack.dimensions)
                tex.upload(context, stack.buffer)
                timestamps[tex] = currentTimestamp
                texturesU8[stack] = tex
                uploaded[tex] = true
                true
            } else {
                logger.debug("Uploading U8 RAII texture")
                val tex = uploadToTextureU8(context, stack.image as RandomAccessibleInterval<UnsignedByteType>, tex8)
                timestamps[tex] = currentTimestamp
                texturesU8[stack] = tex
                uploaded[tex] = true
                true
            }
        }

        if (type is UnsignedShortType && !(tex16 == null || uploaded.getOrDefault(tex16, false))) {
            return if (stack is BufferedSimpleStack3D) {
                logger.info("Uploading U16 buffered texture")
                val tex = tex16 ?: VolumeTextureU16()
                tex.init(stack.dimensions)
                tex.upload(context, stack.buffer)
                timestamps[tex] = currentTimestamp
                texturesU16[stack] = tex
                uploaded[tex] = true
                true
            } else {
                logger.debug("Uploading U16 RAII texture")
                val tex = uploadToTextureU16(context, stack.image as RandomAccessibleInterval<UnsignedShortType>, tex16)
                timestamps[tex] = currentTimestamp
                texturesU16[stack] = tex
                uploaded[tex] = true
                true
            }
        }

        if (type is ARGBType && !(texARGB == null || uploaded.getOrDefault(texARGB, false))) {
            return if (stack is BufferedSimpleStack3D) {
                TODO("Not implemented upload for buffered ARGB textures yet")
                true
            } else {
                val tex = uploadToTextureRGBA8(context, stack.image as RandomAccessibleInterval<ARGBType>, texARGB)
                timestamps[tex] = currentTimestamp
                texturesRGBA8[stack] = tex
                uploaded[tex] = true
                true
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

    private fun uploadToTextureU16(context : GpuContext, rai : RandomAccessibleInterval<UnsignedShortType>, t: VolumeTextureU16? = null) : VolumeTextureU16 {
        val texture = if(t == null) {
            val vt = VolumeTextureU16()
            vt.init(Intervals.dimensionsAsIntArray(rai))
            vt
        } else {
            t
        }

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

    private fun uploadToTextureU8(context : GpuContext, rai : RandomAccessibleInterval<UnsignedByteType>, t: VolumeTextureU8? = null) : VolumeTextureU8 {
        val texture = if(t == null) {
            val vt = VolumeTextureU8()
            vt.init(Intervals.dimensionsAsIntArray(rai))
            vt
        } else {
            t
        }

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

    private fun uploadToTextureRGBA8(context : GpuContext, rai : RandomAccessibleInterval<ARGBType>, t: VolumeTextureRGBA8? = null) : VolumeTextureRGBA8 {
        val texture = if(t == null) {
            val vt = VolumeTextureRGBA8()
            vt.init(Intervals.dimensionsAsIntArray(rai))
            vt
        } else {
            t
        }

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
