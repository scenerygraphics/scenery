package graphics.scenery.volumes

import bdv.tools.brightness.ConverterSetup
import graphics.scenery.Hub
import graphics.scenery.OrientedBoundingBox
import graphics.scenery.Origin
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.times
import net.imglib2.type.numeric.integer.UnsignedByteType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import tpietzsch.example2.VolumeViewerOptions

class RAIVolume(@Transient val ds: VolumeDataSource.RAISource<*>, options: VolumeViewerOptions, hub: Hub): Volume(
    ds,
    options,
    hub
) {
    private constructor() : this(VolumeDataSource.RAISource(UnsignedByteType(), emptyList(), ArrayList<ConverterSetup>(), 0, null), VolumeViewerOptions.options(), Hub()) {

    }

    init {
        name = "Volume (RAI source)"
        if(ds.cacheControl != null) {
            logger.debug("Adding cache control")
            cacheControls.addCacheControl(ds.cacheControl)
        }

        timepointCount = ds.numTimepoints

        boundingBox = generateBoundingBox()
    }

    override fun generateBoundingBox(): OrientedBoundingBox {
        return OrientedBoundingBox(this,
            Vector3f(-0.0f, -0.0f, -0.0f),
            Vector3f(getDimensions()))
    }

    override fun localScale(): Vector3f {
        val size = getDimensions()
        logger.info("Sizes are $size")

        return Vector3f(
                size.x() * pixelToWorldRatio / 10.0f,
                -1.0f * size.y() * pixelToWorldRatio / 10.0f,
                size.z() * pixelToWorldRatio / 10.0f
        )
    }

    override fun getDimensions(): Vector3i {
        val source = ds.sources.firstOrNull()

        return if(source != null) {
            val s = source.spimSource.getSource(0, 0)
            val min = Vector3i(s.min(0).toInt(), s.min(1).toInt(), s.min(2).toInt())
            val max = Vector3i(s.max(0).toInt(), s.max(1).toInt(), s.max(2).toInt())
            max.sub(min)
        } else {
            Vector3i(1, 1, 1)
        }
    }

    override fun createSpatial(): VolumeSpatial {
        return RAIVolumeSpatial(this)
    }

    class RAIVolumeSpatial(volume: RAIVolume): VolumeSpatial(volume) {
        override fun composeModel() {
            @Suppress("SENSELESS_COMPARISON")
            if (position != null && rotation != null && scale != null ) {
                val volume = (node as? RAIVolume) ?: return
                val source = volume.ds.sources.firstOrNull()

                val shift = if (source != null) {
                    val s = source.spimSource.getSource(0, 0)
                    val min = Vector3f(s.min(0).toFloat(), s.min(1).toFloat(), s.min(2).toFloat())
                    val max = Vector3f(s.max(0).toFloat(), s.max(1).toFloat(), s.max(2).toFloat())
                    (max - min) * (-0.5f)
                } else {
                    Vector3f(0.0f, 0.0f, 0.0f)
                }

                model.translation(position)
                model.mul(Matrix4f().set(this.rotation))
                model.scale(scale)
                model.scale(volume.localScale())
                if (volume.origin == Origin.Center) {
                    model.translate(shift)
                }

            }
        }
    }
}
