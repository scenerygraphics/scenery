package graphics.scenery.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.Hub
import graphics.scenery.Node
import graphics.scenery.utils.LazyLogger
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import org.joml.Quaternionf
import org.joml.Vector3f
import tpietzsch.example2.VolumeViewerOptions

class VolumeSerializer: Serializer<Volume>() {
    private val logger by LazyLogger()
//    init {
//        acceptsNull = false
//    }
    /** Writes the bytes for the object to the output.
     *
     *
     * This method should not be called directly, instead this serializer can be passed to [Kryo] write methods that accept a
     * serialier.
     * @param volume May be null if [.getAcceptsNull] is true.
     */
    override fun write(kryo: Kryo, output: Output, volume: Volume) {
        volume.spatial {
            kryo.writeClassAndObject(output, position)
            kryo.writeClassAndObject(output, rotation)
            kryo.writeClassAndObject(output, scale)
        }
        kryo.writeClassAndObject(output, volume.transferFunction)
        kryo.writeClassAndObject(output, volume.colormap)
        kryo.writeObject(output, volume.pixelToWorldRatio)
        kryo.writeObject(output, volume.currentTimepoint)
    }

    /** Reads bytes and returns a new object of the specified concrete type.
     *
     *
     * Before Kryo can be used to read child objects, [Kryo.reference] must be called with the parent object to
     * ensure it can be referenced by the child objects. Any serializer that uses [Kryo] to read a child object may need to
     * be reentrant.
     *
     *
     * This method should not be called directly, instead this serializer can be passed to [Kryo] read methods that accept a
     * serialier.
     * @return May be null if [.getAcceptsNull] is true.
     */
    override fun read(kryo: Kryo, input: Input, type: Class<out Volume>): Volume {
        val position = kryo.readClassAndObject(input) as Vector3f
        val rotation = kryo.readClassAndObject(input) as Quaternionf
        val scale = kryo.readClassAndObject(input) as Vector3f
        val tf = kryo.readClassAndObject(input) as TransferFunction
        val colormap = kryo.readClassAndObject(input) as Colormap
        val pixelToWorldRatio = kryo.readObject(input, Float::class.java)
        val timepoint = kryo.readObject(input, Int::class.java)

        logger.info("TP=$timepoint")

        val vol = Volume(Volume.VolumeDataSource.NullSource(timepoint+1), VolumeViewerOptions(), Hub())
        vol.transferFunction = tf
        vol.colormap = colormap
        vol.pixelToWorldRatio = pixelToWorldRatio
        vol.spatial {
            this.position = position
            this.scale = scale
            this.rotation = rotation
        }
        vol.currentTimepoint = timepoint

        return vol
    }
}
