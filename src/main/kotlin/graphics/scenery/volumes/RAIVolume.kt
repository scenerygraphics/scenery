package graphics.scenery.volumes

import graphics.scenery.Hub
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.times
import org.joml.Matrix4f
import org.joml.Vector3f
import tpietzsch.example2.VolumeViewerOptions

class RAIVolume(val ds: VolumeDataSource.RAISource<*>, options: VolumeViewerOptions, hub: Hub): Volume(ds, options, hub) {
    init {
        name = "Volume (RAI source)"
        if(ds.cacheControl != null) {
            logger.info("Adding cache control")
            cacheControls.addCacheControl(ds.cacheControl)
        }
    }

    override fun localScale(): Vector3f {
        var size = Vector3f(1.0f, 1.0f, 1.0f)
        val source = ds.sources.firstOrNull()

        if(source != null) {
            val s = source.spimSource.getSource(0, 0)
            val min = Vector3f(s.min(0).toFloat(), s.min(1).toFloat(), s.min(2).toFloat())
            val max = Vector3f(s.max(0).toFloat(), s.max(1).toFloat(), s.max(2).toFloat())
            size = max - min
        }
        logger.info("Sizes are $size")

        return Vector3f(
                size.x() * pixelToWorldRatio / 10.0f,
                size.y() * pixelToWorldRatio / 10.0f,
                size.z() * pixelToWorldRatio / 10.0f
        )
    }

    override fun composeModel() {
        logger.info("Composing model for $this")
        @Suppress("SENSELESS_COMPARISON")
        if(position != null && rotation != null && scale != null) {
            val L = localScale()
            logger.info("Local scale is $L")
            val Lh = L * (1.0f/2.0f)
            model.identity()
            model.translate(this.position.x(), this.position.y(), this.position.z())
            model.mul(Matrix4f().set(this.rotation))
            model.scale(this.scale.x(), this.scale.y(), this.scale.z())
            model.scale(Lh.x(), Lh.y(), Lh.z())
            model.translate(-L.x()/pixelToWorldRatio*5.0f, -L.y()/pixelToWorldRatio*5.0f, -L.z()/pixelToWorldRatio*5.0f)
        }
    }
}
